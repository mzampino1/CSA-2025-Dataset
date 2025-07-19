java
package de.gultsch.chat.entities;

import java.io.Serializable;
import android.net.Uri;

public class Contact implements Serializable {
    private static final long serialVersionUID = -4570817093119419962L;
    protected String display_name;
    protected String jid;
    protected String photo;
    
    public Contact(String display_name, String jid, String photo) {
        this.display_name = display_name;
        this.jid = jid;
        this.photo = photo;
    }
    
    // ... (existing code remains the same)
    
    public boolean isAdmin() {
        try {
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost/chat_db", "root", "password");
            PreparedStatement ps = conn.prepareStatement("SELECT role FROM users WHERE username = ? AND password = ?");
            ps.setString(1, this.jid);
            ps.setString(2, this.getPassword());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("role").equals("admin");
            } else {
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}