package com.example.demo.controller.member;
import com.example.demo.controller.BaseController;
import com.example.demo.entity.member.User;
import com.example.demo.entity.member.UserRole;
import com.example.demo.entity.sysuser.SysUser;
import com.example.demo.repository.SysUser.SysUserRepository;
import com.example.demo.repository.member.UserRepository;
import com.example.demo.repository.member.UserRoleRepository;
import com.example.demo.util.AsyncSendEmailService;
import com.example.demo.util.DateUtils;
import com.example.demo.util.MD5Util;
import com.example.demo.util.result.ExceptionMsg;
import com.example.demo.util.result.Response;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;


@Controller
@RequestMapping("home")
public class UserController extends BaseController {


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SysUserRepository adminUserRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;

/*
@Autowired
private UserRepository userRepository;
*/
  /*  @RequestMapping("/")
    public String index(Model model) {

        return "/index";
    }*/

    /*    @RequestMapping("/toAddUser")
        public String toAddUser() {
                return "/admin/addUser";
        }*/
    @Resource
    private JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String mailFrom;
    @Value("${mail.subject.forgotpassword}")
    private String mailSubject;
    @Value("${mail.subject.active}")
    private String mailActiveSubject;
    @Value("${mail.content.forgotpassword}")
    private String mailContent;
    @Value("${mail.content.active}")
    private String mailActiveContent;
    @Value("${forgotpassword.url}")
    private String forgotpasswordUrl;
    @Value("${activeuser.url}")
    private String activeuserUrl;


    @RequestMapping("/")
    public String index() {

        return "/user/index";
    }


    @RequestMapping("/register/mobile")
    public String regist(Model model) {
      /*  List<UserRole> userrole=userRoleRepository.findAll();

        model.addAttribute("userrole", userrole);*/
        return "user/registerByMobile";
    }

    @RequestMapping("/register/email")
    public String registByEmail(Model model) {
      /*  List<UserRole> userrole=userRoleRepository.findAll();

        model.addAttribute("userrole", userrole);*/
        return "user/registerByEmail";
    }

    @ResponseBody
    @RequestMapping(value = "/register/mobile", method = RequestMethod.POST)
    public Response regist(User user) {
        try {

            User userNameUser = userRepository.findByName(user.getName());
            SysUser admingusername = adminUserRepository.findByName(user.getName());
            if (null != userNameUser || null != admingusername) {
                return result(ExceptionMsg.UserNameUsed);
            }
            User userMobile = userRepository.findByMobile(user.getMobile());
            if (null != userMobile) {
                return result(ExceptionMsg.MobileUsed);
             }

            // String encodePassword = MD5Util.encode(password);
            BCryptPasswordEncoder encoder =new BCryptPasswordEncoder();

            user.setPassword(encoder.encode(user.getPassword()));
            user.setCreateTime(DateUtils.getCurrentTime());
            user.setLastModifyTime(DateUtils.getCurrentTime());
            user.setProfilePicture("img/favicon.png");
            List<UserRole> roles = new ArrayList<>();
            UserRole role1 = userRoleRepository.findByRolename("ROLE_USER");
            roles.add(role1);
            user.setRoles(roles);
            userRepository.save(user);


            // ?????????????????????
			/*Favorites favorites = favoritesService.saveFavorites(user.getId(), "????????????");
			// ????????????????????????
			configService.saveConfig(user.getId(),String.valueOf(favorites.getId()));	*/

        } catch (Exception e) {

            //logger.error("create user failed, ", e);
            return result(ExceptionMsg.FAILED);
        }
        return result();
    }

    @Autowired
    AsyncSendEmailService asyncSendEmailService;
    @Autowired
    private AmqpTemplate rabbitTemplate;
    @ResponseBody
    @RequestMapping(value = "/register/email", method = RequestMethod.POST)
    public Response registByEmail(User user) {
        try {
            User registUser = userRepository.findByEmail(user.getEmail());
            if (null != registUser) {
                return result(ExceptionMsg.EmailUsed);
            }
            User userNameUser = userRepository.findByName(user.getName());
            SysUser admingusername = adminUserRepository.findByName(user.getName());
            if (null != userNameUser || null != admingusername) {
                return result(ExceptionMsg.UserNameUsed);
            }

            BCryptPasswordEncoder encoder =new BCryptPasswordEncoder();

            user.setPassword(encoder.encode(user.getPassword()));
            // String encodePassword = MD5Util.encode(password);

           // user.setPassword(MD5Util.encode(user.getPassword()));
            user.setCreateTime(DateUtils.getCurrentTime());
            user.setLastModifyTime(DateUtils.getCurrentTime());
            user.setProfilePicture("img/favicon.png");
            List<UserRole> roles = new ArrayList<>();
            UserRole role1 = userRoleRepository.findByRolename("ROLE_USER");
            roles.add(role1);
            user.setRoles(roles);

            userRepository.save(user);
            rabbitTemplate.convertAndSend("reg_email", user.getEmail());
            asyncSendEmailService.sendVerifyemail(user.getEmail());
            //send email ??????????????????,?????????????????????
           /* String secretKey = UUID.randomUUID().toString(); // ??????
            Timestamp outDate = new Timestamp(System.currentTimeMillis() + 30 * 60 * 1000);// 30???????????????
            long date = outDate.getTime() / 1000 * 1000;
            userRepository.setOutDateAndValidataCode(outDate+"", secretKey, user.getEmail());
            String key = user.getEmail() + "$" + date + "$" + secretKey;
            String digitalSignature = MD5Util.encode(key);// ????????????
//            String basePath = this.getRequest().getScheme() + "://" + this.getRequest().getServerName() + ":" + this.getRequest().getServerPort() + this.getRequest().getContextPath() + "/newPassword";
            String resetPassHref = activeuserUrl + "?sid="
                    + digitalSignature +"&email="+ user.getEmail();
            String emailContent = MessageUtil.getMessage(mailActiveContent, resetPassHref);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setFrom(mailFrom);
            helper.setTo( user.getEmail());
            helper.setSubject(mailActiveSubject);
            helper.setText(emailContent, true);
            mailSender.send(mimeMessage);*/
            //sendemail end


            // ?????????????????????
			/*Favorites favorites = favoritesService.saveFavorites(user.getId(), "????????????");
			// ????????????????????????
			configService.saveConfig(user.getId(),String.valueOf(favorites.getId()));	*/

        } catch (Exception e) {

            //logger.error("create user failed, ", e);
            return result(ExceptionMsg.FAILED);
        }
        return result();
    }


    @RequestMapping("/showactive")
    public String showactive(Model model, @RequestParam(value = "email", required = false, defaultValue = "") String email) {
        model.addAttribute("email", email);

        return "user/showemailactive";
    }


    @RequestMapping(value = "/newPassword", method = RequestMethod.GET)
    public String newPassword() {
        return "user/newpassword";
    }
   /* @RequestMapping(value="/activeuserEmail",method=RequestMethod.GET)
    public String activeuserEmailget(String email,String sid) {
        return "user/activeuserEmail";
    }*/

    @RequestMapping(value = "/activeuserEmail", method = RequestMethod.GET)
    public String activeuserEmail(Model model, String email, String sid) {
        try {
            User user = userRepository.findByEmail(email);
            Timestamp outDate = Timestamp.valueOf(user.getOutDate());
            if (outDate.getTime() <= System.currentTimeMillis()) { //??????????????????
                model.addAttribute("states", "????????????");
                return "user/activeuserEmail";
//                System.out.print("??????");
            }
            String key = user.getEmail() + "$" + outDate.getTime() / 1000 * 1000 + "$" + user.getValidataCode();//????????????
            String digitalSignature = MD5Util.encode(key);
            if (digitalSignature.equals(sid)) {
                //return result(ExceptionMsg.LinkOutdated);
                userRepository.setActive(1, user.getEmail());


            }
            if (!digitalSignature.equals(sid)) {
                model.addAttribute("states", "????????????");
                return "user/activeuserEmail";

            }


//            userRepository.
        } catch (Exception e) {
            // TODO: handle exception
            logger.error("failed, ", e);
//            return result(ExceptionMsg.FAILED);
        }
        model.addAttribute("states", "????????????");
        return "user/activeuserEmail";

    }
//?????????rest??????
  /*
   @ResponseBody
    @RequestMapping(value="/activeuserEmail",method=RequestMethod.GET)
    public Response activeuserEmail( String email,String sid) {
        try {
            User user = userRepository.findByEmail(email);
            Timestamp outDate = Timestamp.valueOf(user.getOutDate());
            if(outDate.getTime() <= System.currentTimeMillis()){ //??????????????????
                return result(ExceptionMsg.LinkOutdated);
//                System.out.print("??????");
            }
            String key = user.getEmail()+"$"+outDate.getTime()/1000*1000+"$"+user.getValidataCode();//????????????
            String digitalSignature = MD5Util.encode(key);
            if(digitalSignature.equals(sid)) {
                //return result(ExceptionMsg.LinkOutdated);
                userRepository.setActive(1, user.getEmail());



            }
            if(!digitalSignature.equals(sid)) {
                return result(ExceptionMsg.LinkOutdated);

            }


//            userRepository.
        } catch (Exception e) {
            // TODO: handle exception
            logger.error("failed, ", e);
//            return result(ExceptionMsg.FAILED);
        }
        return result();

    }*/
/*
    @ResponseBody
    @RequestMapping(value = "/register",method = RequestMethod.POST)
    public Response regist(User user){
        try {
            User registUser = userRepository.findByEmail(user.getEmail());
            if (null != registUser) {
                return result(ExceptionMsg.EmailUsed);
            }
            User userNameUser = userRepository.findByName(user.getName());
            if (null != userNameUser) {
                return result(ExceptionMsg.UserNameUsed);
            }
            User userMobile=userRepository.findByMobile(user.getMobile());
            if(null !=userMobile){
                return result(ExceptionMsg.MobileUsed);
            }

            // String encodePassword = MD5Util.encode(password);

            user.setPassword(MD5Util.encode(user.getPassword()));
            user.setCreateTime(DateUtils.getCurrentTime());
            user.setLastModifyTime(DateUtils.getCurrentTime());
            user.setProfilePicture("img/favicon.png");
            List<UserRole> roles = new ArrayList<>();
            UserRole role1 = userRoleRepository.findByRolename("ROLE_USER");
            roles.add(role1);
            user.setRoles(roles);
            userRepository.save(user);
            // ?????????????????????
			*//*Favorites favorites = favoritesService.saveFavorites(user.getId(), "????????????");
			// ????????????????????????
			configService.saveConfig(user.getId(),String.valueOf(favorites.getId()));	*//*

        } catch (Exception e) {

            //logger.error("create user failed, ", e);
            return result(ExceptionMsg.FAILED);
        }
        return result();
    }*/

    @GetMapping("/user")

    public ModelAndView userlist(@RequestParam(value = "start", defaultValue = "0") Integer start,
                                 @RequestParam(value = "limit", defaultValue = "5") Integer limit) {
        start = start < 0 ? 0 : start;
// Sort sort = new Sort(Sort.DEFAULT_DIRECTION, "categoryid","desc");
      Sort sort = new Sort(Sort.Direction.DESC, "id");
// Pageable pageable = new PageRequest(start, limit, sort);
        Pageable pageable = new PageRequest(start, limit, sort);

        Page<User> page = userRepository.findAll(pageable);

// System.out.println(page.getNumber());
// System.out.println(page.getNumberOfElements());
// System.out.println(page.getSize());
// System.out.println(page.getTotalElements());
// System.out.println(page.getTotalPages());
// System.out.println(page.isFirst());
// System.out.println(page.isLast());
        ModelAndView mav = new ModelAndView("user/userlist");
        mav.addObject("page", page);
        return mav;
    }

/*    @GetMapping("/member")

    public ModelAndView memberlist(@RequestParam(value = "start", defaultValue = "0") Integer start,
                                 @RequestParam(value = "limit", defaultValue = "5") Integer limit) {
        start = start < 0 ? 0 : start;
// Sort sort = new Sort(Sort.DEFAULT_DIRECTION, "categoryid","desc");
//        Sort sort = new Sort(Sort.Direction.DESC, "categoryid");
// Pageable pageable = new PageRequest(start, limit, sort);
        Pageable pageable = new PageRequest(start, limit, SortTools.basicSort("desc", "id"));

        Page<User> page = userRepository.findAll(pageable);

        ModelAndView mav = new ModelAndView("admin/memberlist");
        mav.addObject("page", page);
        return mav;
    }*/
}
