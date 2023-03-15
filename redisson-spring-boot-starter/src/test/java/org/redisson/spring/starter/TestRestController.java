package org.redisson.spring.starter;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * @author Nikita Koksharov
 */
@RestController
@RequestMapping("/api")
public class TestRestController {

    @GetMapping("/set")
    public void setData(HttpSession httpSession) {
        httpSession.setAttribute("testattr", "1");
    }

    @GetMapping("/get")
    public String getData(HttpSession httpSession) {
        return (String) httpSession.getAttribute("testattr");
    }
}