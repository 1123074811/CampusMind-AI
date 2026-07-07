package cn.campusmind.auth.domain;

public enum UserStatus {
    DISABLED(0),
    ENABLED(1);

    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static UserStatus fromCode(int code) {
        return code == ENABLED.code ? ENABLED : DISABLED;
    }
}
