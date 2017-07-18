package ru.javaops.web;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import ru.javaops.SqlResult;
import ru.javaops.model.Group;
import ru.javaops.model.Project;
import ru.javaops.model.User;
import ru.javaops.repository.UserRepository;
import ru.javaops.service.CachedGroups;
import ru.javaops.service.PartnerService;
import ru.javaops.service.SqlService;
import ru.javaops.to.UserAdminsInfo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * gkislin
 * 15.07.2017
 */
@Controller
@Slf4j
public class PartnerController {
    @Autowired
    private PartnerService partnerService;

    @Autowired
    private SqlService sqlService;

    @Autowired
    private CachedGroups cachedGroups;

    @Autowired
    private UserRepository userRepository;

    @GetMapping(value = "/user")
    public ModelAndView userInfo(@RequestParam("email") String email,
                                 @RequestParam("partnerKey") String partnerKey) {

        User partner = partnerService.checkPartner(partnerKey);
        User user = userRepository.findByEmailWithGroup(email);
        Map<Integer, Group> groupMembers = cachedGroups.getMembers();
        List<Project> projects = user.getUserGroups().stream()
                .filter(ug -> groupMembers.containsKey(ug.getGroup().getId()))
                .map(ug -> groupMembers.get(ug.getGroup().getId()).getProject())
                .distinct()
                .collect(Collectors.toList());
        return new ModelAndView("userInfo",
                ImmutableMap.of("user", user, "projects", projects, "partner", partner));
    }

    @PostMapping(value = "/saveAdminInfo")
    public String saveComment(@RequestParam("email") String email,
                              @RequestParam("adminKey") String adminKey,
                              UserAdminsInfo uaInfo) {
//        userRepository.saveAdminInfo(email, uaInfo);
        userRepository.saveAdminInfo(email, uaInfo.getComment(), uaInfo.getMark(), uaInfo.getBonus());
        return "closeWindow";
    }

    @GetMapping(value = "/sql")
    public ModelAndView sqlExecute(@RequestParam("sql_key") String sqlKey,
                                   @RequestParam(value = "limit", required = false) Integer limit,
                                   @RequestParam(value = "csv", required = false, defaultValue = "false") boolean csv,
                                   @RequestParam("partnerKey") String partnerKey,
                                   @RequestParam(value = "fromDate", required = false) String fromDate,
                                   @RequestParam Map<String, String> params) {

        User partner = partnerService.checkPartner(partnerKey);
        params.put("partnerKey", partnerKey);
        params.put("partnerMark", partner.getMark());
        params.put("fromDate", fromDate == null ? "01-01-01" : fromDate);
        params.put("toDate", fromDate == null ? "3000-01-01" : DateTimeFormatter.ISO_DATE.format(LocalDate.now()));
        SqlResult result = sqlService.execute(sqlKey, limit, params);
        return new ModelAndView("sqlResult",
                ImmutableMap.of("result", result, "csv", csv));
    }
}