package cn.ninanina.wushan.web.result;

public enum ResultMsg {

    SUCCESS("000000", "操作成功"),
    FAILED("999999", "操作失败"),
    ParamError("000001", "参数错误"),

    INVALID_VIDEO_ID("000002", "无效视频id"),
    NOT_LOGIN("000003", "未登录"),
    EMPTY_CONTENT("000004", "空内容错误"),
    COLLECT_SUCCESS("000006", "收藏成功"),
    COLLECT_CANCEL("000007", "取消收藏成功"),

    SECRET_INVALID("000008", "secret值无效"),
    APPKEY_INVALID("000009", "appKey无效");

    ResultMsg(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    private final String code;
    private final String msg;

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}