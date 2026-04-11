import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;

public class CheckEncoding {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide file path");
            return;
        }
        Path path = Paths.get(args[0]);
        System.out.println("Checking file: " + path);
        
        try {
            byte[] bytes = Files.readAllBytes(path);
            System.out.println("File size: " + bytes.length + " bytes");
            
            // Check UTF-8
            try {
                String s = new String(bytes, StandardCharsets.UTF_8);
                s.length(); // force evaluation
                System.out.println("UTF-8 decoding successful");
            } catch (Exception e) {
                System.out.println("UTF-8 decoding failed: " + e.getMessage());
            }
            
            // Check GB18030
            try {
                String s = new String(bytes, Charset.forName("GB18030"));
                s.length();
                System.out.println("GB18030 decoding successful");
            } catch (Exception e) {
                System.out.println("GB18030 decoding failed: " + e.getMessage());
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
