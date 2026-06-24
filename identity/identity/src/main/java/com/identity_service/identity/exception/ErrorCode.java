package com.identity_service.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {
    USER_EXISTED(1000 , "User name/email existed" , HttpStatus.BAD_REQUEST),
    USER_NOT_EXIST(1001 , "User is not existed" , HttpStatus.NOT_FOUND),
    AUTHENTICATED_FAILED(1002 , "Username or password incorrect" , HttpStatus.NOT_FOUND),
    CREATE_TOKEN_FAILED(1003 ,"Failed when create token" , HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(1007, "Token invalid" , HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(1008 , "Token expiration timed out" , HttpStatus.BAD_REQUEST),
    TOKEN_TYPE_INVALID(1009 , "Token type is not refresh" , HttpStatus.BAD_REQUEST),
    TOKEN_NOT_FOUND(1010 , "Refresh token not found in DB" , HttpStatus.BAD_REQUEST),
    TOKEN_REVOKED(1011 , "Token exists in blacklists" , HttpStatus.BAD_REQUEST),
     VERIFY_EMAIL_TOKEN_INVALID(1012 , "Email token invalid" , HttpStatus.NOT_FOUND),
    EMAIL_NOT_VERIFIED(1013 , "Email chưa được xác thực. Vui lòng nhập mã OTP đã gửi về email." , HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED(1014 , "Tài khoản đã bị khóa do vi phạm chính sách cộng đồng" , HttpStatus.FORBIDDEN),
    OTP_INVALID(1015 , "Mã OTP không đúng" , HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(1016 , "Mã OTP đã hết hạn. Vui lòng gửi lại mã mới." , HttpStatus.BAD_REQUEST),
    EMAIL_SEND_FAILED(1017 , "Không thể gửi email xác thực. Vui lòng thử lại sau." , HttpStatus.INTERNAL_SERVER_ERROR),
    PASSWORD_INVALID(1018 , "Mật khẩu mới phải có ít nhất 6 ký tự" , HttpStatus.BAD_REQUEST),
    PASSWORD_RESET_TOKEN_INVALID(1019 , "Không tìm thấy yêu cầu đặt lại mật khẩu. Vui lòng gửi lại mã OTP." , HttpStatus.BAD_REQUEST)



    ;

    private final int code;
    private final String message;
    private final HttpStatusCode httpStatusCode;

    ErrorCode(int code , String message , HttpStatusCode httpStatusCode){
        this.code = code ;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatusCode getHttpStatusCode() {
        return httpStatusCode;
    }
}
