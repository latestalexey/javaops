package ru.javaops.to;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.SafeHtml;
import ru.javaops.model.User;
import ru.javaops.util.UserUtil;

/**
 * GKislin
 * 16.02.2016
 */
@Getter
@Setter
@NoArgsConstructor
public class UserTo {
    @NotEmpty(message = "Поле email не может быть пустым")
    @Email
    @SafeHtml
    private String email;

    @SafeHtml
    private String nameSurname;
    @SafeHtml
    private String location;
    @SafeHtml
    private String phone;
    @SafeHtml
    private String skype;

    public UserTo(String email, String nameSurname) {
        this.email = email;
        this.nameSurname = nameSurname;
    }

    public void setEmail(String email) {
        this.email = email.toLowerCase();
    }

    public void setLocation(String location) {
        this.location = location == null ? null : location.trim().toLowerCase();
    }

    @Override
    public String toString() {
        return "(email:" + email + ", name:" + nameSurname + ')';
    }

    public User toUser() {
        return UserUtil.createFromTo(this);
    }
}
