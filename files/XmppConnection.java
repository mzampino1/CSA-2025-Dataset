package org.owasp.webgoat;

import org.apache.commons.fileupload.FileItem;
import org.owasp.webgoat.assignments.AssignmentEndpoint;
import org.owasp.webgoat.assignments.AttackResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.io.File;
import java.nio.file.Paths;
import org.apache.commons.lang3.StringUtils;

@RestController
public class Assignment1 extends AssignmentEndpoint {

    private String fileContents = "";

    @GetMapping("/Assignment1/attack1")
    public AttackResult completed(@RequestParam(name = "query", required = false) String query) {
        return success(this).build();
    }

    @PostMapping("/Assignment1/attack2")
    public AttackResult completed(@RequestBody FileItem fileItem, HttpServletRequest request) {
        Map<String, String> details = new HashMap<>();
        if (!fileItem.getName().endsWith(".js")) {
            return failed(this).output("File uploaded successfully.").build();
        } else {
            File jsFile = Paths.get(request.getServletContext().getRealPath("/js") + "/" + fileItem.getName()).toFile();
            if (jsFile.exists()) {
                details.put("fileContents", this.fileContents);
                return failed(this).output("File already exists.").details(details).build();
            } else {
                return success(this).build();
            }
        }
    }
}