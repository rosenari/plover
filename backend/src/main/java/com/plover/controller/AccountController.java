package com.plover.controller;

import com.plover.model.user.UserDto;
import com.plover.model.user.request.*;
import com.plover.service.UserService;
import com.plover.utils.CookieUtil;
import com.plover.utils.JwtUtil;
import com.plover.utils.RedisUtil;
import com.sun.istack.NotNull;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@ApiResponses(value = {
        @ApiResponse(code = 401, message = "Unauthorized", response = Response.class),
        @ApiResponse(code = 403, message = "Forbidden", response = Response.class),
        @ApiResponse(code = 404, message = "Not Found", response = Response.class),
        @ApiResponse(code = 500, message = "Failure", response = Response.class) })


//개발 모드에서는 모두 허용
//@CrossOrigin(origins = {"*"})
@RestController
@RequestMapping("account")
public class AccountController {


    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserService userService;
    @Autowired
    private CookieUtil cookieUtil;
    @Autowired
    private RedisUtil redisUtil;



    //postMapping으로 변경
    @PostMapping("/login")
    @ApiOperation(
            value = "로그인",
            notes = "post로  LoginRequest 형태의 데이터를 받아서 로그인 처리와 토큰을 발급해 준다.",
            response = Response.class
    )
    public Object login(@Valid LoginRequest userRequest, HttpServletRequest request, HttpServletResponse response) {

        try {
            final UserDto user = userService.login(userRequest.getEmail(), userRequest.getPassword());
            final String token = jwtUtil.generateToken(user);
            final String refreshJwt = jwtUtil.generateRefreshToken(user);

            Cookie accessToken = cookieUtil.createCookie(JwtUtil.ACCESS_TOKEN_NAME, token);
            Cookie refreshToken = cookieUtil.createCookie(JwtUtil.REFRESH_TOKEN_NAME, refreshJwt);

            redisUtil.setDataExpire(refreshJwt, user.getEmail(), JwtUtil.REFRESH_TOKEN_VALIDATION_SECOND);

            response.addCookie(accessToken);
            response.addCookie(refreshToken);

            return new ResponseEntity<>(new Response("success", "로그인에 성공했습니다.", token), HttpStatus.OK);
        } catch(Exception e) {
            return new ResponseEntity<>(new Response("error", "로그인에 실패했습니다. 아이디/비밀번호 확인", e.getMessage()), HttpStatus.UNAUTHORIZED);
        }
    }
    @GetMapping("/checkDupEmail")
    @ApiOperation(value = "이메일 중복 체크",
            notes = "회원가입시 이메일의 중복을 확인한다.",
            response = Response.class)
    public Object checkDupEmail(@Valid @Email @RequestParam String email) {
        ResponseEntity response = null;
        if (!userService.existsByEmail(email)) {
            final Response result = new Response("success", "사용 가능", null);
            response = new ResponseEntity<>(result, HttpStatus.OK);
        } else {
            final Response result = new Response("error", "이메일 중복", null);
            //TODO : HttpStatus 변경하기
            response = new ResponseEntity<>(result , HttpStatus.NOT_ACCEPTABLE);
        }
        return response;
    }
    @GetMapping("/checkDupNickName")
    @ApiOperation(value = "닉네임 중복 체크",
            notes = "회원가입시 닉네임의 중복을 확인한다.",
            response = Response.class)
    public Object checkDupNickName(@Valid @NotNull  @RequestParam String nickName) {
        ResponseEntity response = null;
        if (!userService.existsByNickName(nickName)) {
            final Response result = new Response("success", "사용 가능", null);
            response = new ResponseEntity<>(result, HttpStatus.OK);
        } else {
            final Response result = new Response("error", "닉네임 중복", null);
            //TODO : HttpStatus 변경하기
            response = new ResponseEntity<>(result , HttpStatus.NOT_ACCEPTABLE);
        }
        return response;
    }
    @PostMapping("/signup")
    @ApiOperation(value = "회원가입",
            notes = "회원가입 때 받아야하는 데이터 형태인 SignupRequest로 데이터를 받아서 가입을 진행한다.",
            response = Response.class)
    public Object signup(@RequestBody @Valid SignupRequest userRequest) {

        ResponseEntity<Response> response = null;
        try {
            userService.signup(userRequest);
            final Response result = new Response("success","회원가입 성공", null);
            response = new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e) {
            final Response result = new Response("success","회원가입 중 오류 발생", e.getMessage());
            response = new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
        return response;
    }


    @PostMapping("/verify")
    @ApiOperation(value = "이메일 인증 메일 발송",
            notes = "회원가입 후 이메일 인증 메일을 발송한다.",
            response = Response.class)
    public Response verify(@RequestBody VerifyEmailRequest verifyEmailRequest, HttpServletRequest req, HttpServletResponse res) {
        Response response;
        try {
            UserDto user = userService.findUserByEmail(verifyEmailRequest.getEmail());
            userService.sendVerificationMail(user);
            response = new Response("success", "성공적으로 인증메일을 보냈습니다.", null);
        } catch (Exception exception) {
            response = new Response("error", "인증메일을 보내는데 문제가 발생했습니다.", exception);
        }
        return response;
    }

    @GetMapping("/verify/{key}")
    @ApiOperation(value = "이메일 인증 확인",
            notes = "이메일에 로그인 후, 주소를 누르면 이메일 인증이 완료된다.",
            response = Response.class)
    public Response getVerify(@PathVariable String key) {
        Response response;
        try {
            userService.verifyEmail(key);
            response = new Response("success", "성공적으로 인증메일을 확인했습니다.", null);

        } catch (Exception e) {
            response = new Response("error", "인증메일을 확인하는데 실패했습니다.", null);
        }
        return response;
    }

    @GetMapping("/password/{key}")
    @ApiOperation(value = "비밀번호 변경 메일 확인",
            notes = "비밀번호 변경메일 발송 후, 메일에 들어가 링크를 누르면 비밀번호 변경이 가능하게 된다.",
            response = Response.class)
    public Response isPasswordUUIdValidate(@PathVariable String key) {
        Response response;
        try {
            if (userService.isPasswordUuidValidate(key))
                response = new Response("success", "정상적인 접근입니다.", null);
            else
                response = new Response("error", "유효하지 않은 Key값입니다.", null);
        } catch (Exception e) {
            response = new Response("error", "유효하지 않은 key값입니다.", null);
        }
        return response;
    }

    @PostMapping("/password")
    @ApiOperation(value = "비밀번호 변경",
            notes = "비밀번호 변경 확인 메일을 전송한다.",
            response = Response.class)
    public Response requestChangePassword(@RequestBody SendChangePasswordRequest SendChangePassowrd) {
        Response response;
        try {
            UserDto user = userService.findUserByNickName(SendChangePassowrd.getNickName());
            if (!user.getEmail().equals(SendChangePassowrd.getEmail())) throw new NoSuchFieldException("");
            userService.requestChangePassword(user);
            response = new Response("success", "성공적으로 사용자의 비밀번호 변경요청을 수행했습니다.", null);
        } catch (NoSuchFieldException e) {
            response = new Response("error", "사용자 정보를 조회할 수 없습니다.", null);
        } catch (Exception e) {
            response = new Response("error", "비밀번호 변경 요청을 할 수 없습니다.", null);
        }
        return response;
    }

    @PutMapping("/password")
    @ApiOperation(value = "비밀번호 변경 진행",
            notes = "비밀번호 변경 확인 링크를 클릭하고 비밀번호를 변경을 진행한다.",
            response = Response.class)
    public Response changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) {
        Response response;
        try{
            UserDto user = userService.findUserByEmail(changePasswordRequest.getEmail());
            userService.changePassword(user,changePasswordRequest.getPassword());
            response = new Response("success","성공적으로 사용자의 비밀번호를 변경했습니다.",null);
        }catch(Exception e){
            response = new Response("error","사용자의 비밀번호를 변경할 수 없었습니다.",null);
        }
        return response;

    }

    //test
    @ApiOperation(value = "다른 페이지로 이동이 가능한지 확인",
            notes = "로그인 / 비로그인 시 이동이 가능한지 확인",
            response = Response.class)
    @GetMapping("/hello")
    public String hello() {
        return "hello ";
    }
}