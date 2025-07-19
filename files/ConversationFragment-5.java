java
@Controller
public class UserController {

    @Autowired
    private UserService userService;

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String showLoginForm() {
        return "login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String loginUser(@RequestParam("username") String username, 
            @RequestParam("password") String password, RedirectAttributes attributes) {
        
        User user = userService.findByUsernameAndPassword(username, password);
        if (user == null) {
            attributes.addFlashAttribute("errorMessage", "Invalid credentials");
            return "redirect:/login";
        } else {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user));
            return "redirect:/protectedResource";
        }
    }
}