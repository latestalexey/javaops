package ru.javaops.web;

import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import ru.javaops.model.*;
import ru.javaops.service.*;
import ru.javaops.to.UserTo;
import ru.javaops.to.UserToExt;
import ru.javaops.util.ProjectUtil.ProjectProps;
import ru.javaops.util.Util;

import javax.mail.MessagingException;
import javax.validation.Valid;
import javax.validation.ValidationException;
import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * GKislin
 */
@Controller
@Slf4j
public class SubscriptionController {

    @Autowired
    private IntegrationService integrationService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private MailService mailService;

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private IdeaCouponService ideaCouponService;

    @Autowired
    private GoogleAdminSDKDirectoryService googleAdminSDKDirectoryService;


    @RequestMapping(value = "/activate", method = RequestMethod.GET)
    public ModelAndView activate(@RequestParam("email") String email, @RequestParam("activate") boolean activate, @RequestParam("key") String key) {
        User u = userService.findExistedByEmail(email);
        if (u.isActive() != activate) {
            u.setActive(activate);
            u.setActivatedDate(new Date());
            userService.save(u);
        }
        return new ModelAndView("activation",
                ImmutableMap.of("activate", activate,
                        "subscriptionUrl", subscriptionService.getSubscriptionUrl(email, key, !activate)));
    }

    @RequestMapping(value = "/register-group", method = RequestMethod.POST)
    public ModelAndView registerInGroup(@RequestParam("group") String group,
                                        @RequestParam(value = "confirmMail", required = false) String confirmMail,
                                        @RequestParam(value = "callback", required = false) String callback,
                                        @RequestParam("channel") String channel,
                                        @RequestParam("template") String template,
                                        @RequestParam("channelKey") String channelKey,
                                        @Valid UserTo userTo, BindingResult result) {
        if (result.hasErrors()) {
            throw new ValidationException(Util.getErrorMessage(result));
        }
        UserGroup userGroup = groupService.registerAtGroup(userTo, group, channel);
        String mailResult = mailService.sendToUser(template, userGroup.getUser());
        ImmutableMap<String, ?> params = ImmutableMap.of("userGroup", userGroup, "result", mailResult);

        final ModelAndView mv;
        if (callback != null) {
            mv = getRedirectView(mailResult, callback, "http://javawebinar.ru/error.html");
        } else {
            mv = new ModelAndView("confirm", params);
        }
        if (confirmMail != null) {
            mailService.sendWithTemplateAsync(confirmMail, "confirm", params);
        }
        return mv;
    }

    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public ModelAndView registerByProject(@RequestParam("project") String project,
                                          @RequestParam("channel") String channel,
                                          @RequestParam(value = "template", required = false) String template,
                                          @Valid UserTo userTo, BindingResult result) {
        if (result.hasErrors()) {
            throw new ValidationException(Util.getErrorMessage(result));
        }

        ProjectProps projectProps = groupService.getProjectProps(project);
        UserGroup userGroup = groupService.registerAtProject(userTo, projectProps, channel);
        String projectName = projectProps.project.getName();

        if (userGroup.getRegisterType() == RegisterType.REPEAT) {
            integrationService.asyncSendSlackInvitation(userGroup.getUser().getEmail(), projectName);
            template = projectName + "_repeat";
        } else if (template == null) {
            template = projectName + "_register";
        }
        String mailResult = mailService.sendToUser(template, userGroup.getUser());
        return getRedirectView(mailResult, "http://javawebinar.ru/confirm.html", "http://javawebinar.ru/error.html");
    }

    private ModelAndView getRedirectView(String mailResult, String successUrl, String failUrl) {
        return new ModelAndView("redirectToUrl", "redirectUrl", MailService.isOk(mailResult) ? successUrl : failUrl);
    }

    @RequestMapping(value = "/repeat", method = RequestMethod.GET)
    public ModelAndView repeat(@RequestParam("email") String email,
                               @RequestParam("project") String projectName) throws MessagingException {

        ProjectProps projectProps = groupService.getProjectProps(projectName);
        User user = userService.findByEmailAndProjectId(email, projectProps.project.getId());
        checkNotNull(user, "Пользователь %s не найден в проекте %s", email, projectName);
        if (userService.findByEmailAndGroupId(email, projectProps.currentGroup.getId()) != null) {
            return new ModelAndView("already_registered");
        }
        mailService.sendToUser(projectName + "_repeat", user);
        groupService.save(user, projectProps.currentGroup, RegisterType.REPEAT, "mail");

        IntegrationService.SlackResponse response = integrationService.sendSlackInvitation(email, projectName);
        return new ModelAndView("registration", "response", response);
    }

    @RequestMapping(value = "/idea", method = RequestMethod.GET)
    public ModelAndView ideaRegister(@RequestParam("email") String email, @RequestParam("group") String groupName) throws MessagingException {
        Group group = groupService.findByName(groupName);
        if (group == null || group.getType() != GroupType.CURRENT) {
            throw new IllegalArgumentException("Для этой группы лицензии IDEA не предусмотрены");
        }
        User user = userService.findByEmailAndGroupId(email, group.getId());
        checkNotNull(user, "Пользователь %s не найден в группе %s", email, groupName);

        IdeaCoupon coupon = ideaCouponService.assignToUser(user, group.getProject());
        String response = mailService.sendWithTemplate(user, "idea_register", ImmutableMap.of("user", user, "coupon", coupon.getCoupon()));
        if (MailService.OK.equals(response)) {
            return new ModelAndView("registration_idea");
        } else {
            throw new IllegalStateException("Ошибка отправки почты" + response);
        }
    }

    @RequestMapping(value = "/profile", method = RequestMethod.GET)
    public ModelAndView participate(@RequestParam("email") String email, @RequestParam("key") String key) {
        User u = userService.findByEmail(email);
        checkNotNull(u, "Пользователь %s не найден", email);
        return new ModelAndView("profile", ImmutableMap.of("user", u, "key", key));
    }

    @RequestMapping(value = "/participate", method = RequestMethod.GET)
    public ModelAndView participate(@RequestParam("email") String email, @RequestParam("key") String key, @RequestParam("project") String projectName) {
        User u = groupService.getUserInProject(email, projectName);
        return new ModelAndView("profile", ImmutableMap.of("user", u, "project", projectService.findByName(projectName), "key", key));
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    public ModelAndView save(@RequestParam("key") String key, @RequestParam(value = "project", required = false) String project, @Valid UserToExt userToExt, BindingResult result) {
        if (result.hasErrors()) {
            throw new ValidationException(Util.getErrorMessage(result));
        }
        userService.update(userToExt);
        if (!Strings.isNullOrEmpty(project)) {
            String email = userToExt.getEmail();
            groupService.getUserInProject(email, project);
            return grantAllAccess(email, userToExt.getGmail(), project, key);
        } else {
            return new ModelAndView("saveProfile", ImmutableMap.of("userToExt", userToExt, "key", key));
        }
    }

    private ModelAndView grantAllAccess(String email, String gmail, String project, String key) {
        log.info("grantAllAccess to {}/{}", email, gmail);
        IntegrationService.SlackResponse response = integrationService.sendSlackInvitation(email, project);
        String accessResponse = "";
        if (!project.equals("javaops")) {
            accessResponse = googleAdminSDKDirectoryService.insertMember(project + "@javaops.ru", gmail);
        }
        return new ModelAndView("registration",
                ImmutableMap.of("response", response, "email", email,
                        "activationKey", key,
                        "accessResponse", accessResponse));
    }
}