package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler extends Thread {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }
    
    // HÀM GHI LỊCH SỬ VÀ TỰ ĐỘNG XÓA LOG QUÁ 30 NGÀY
    private void ghiLichSu(String userInfo, String hanhDong) {
        String dbURL = "jdbc:mysql://localhost:3306/MiniFTP_DB"; 
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(dbURL, "root", "")) {
            java.sql.Statement stmt = conn.createStatement();
            
            // Dọn dẹp lịch sử cũ
            String sqlClean = "DELETE FROM LichSu WHERE ThoiGian < NOW() - INTERVAL 30 DAY";
            stmt.executeUpdate(sqlClean);
            
            // Ghi lịch sử mới
            String sqlInsert = "INSERT INTO LichSu (DanhTinh, HanhDong) VALUES (?, ?)";
            java.sql.PreparedStatement pstmt = conn.prepareStatement(sqlInsert);
            pstmt.setString(1, userInfo);
            pstmt.setString(2, hanhDong);
            pstmt.executeUpdate();
            
        } catch (Exception e) {
            System.out.println("Lỗi ghi Database: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try (
            Socket s = this.socket; 
            DataInputStream dis = new DataInputStream(s.getInputStream());
            DataOutputStream dos = new DataOutputStream(s.getOutputStream())
        ) {
            s.setSoTimeout(30000); 

            String command = dis.readUTF(); 
            String role = dis.readUTF(); 
            
            String clientIP = s.getInetAddress().getHostAddress();
            int port = s.getPort();
            String userInfo = role + " | IP: " + clientIP + " - Port: " + port;
            
            // 1. XỬ LÝ LỆNH UPLOAD
            if (command.equals("UPLOAD")) {
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();
                System.out.println(">> [" + userInfo + "] dang gui file: " + fileName + " (" + fileSize + " bytes)");
                
                File destFile = new File(FTPServer.SERVER_DIR, new File(fileName).getName());
                
                if (destFile.exists()) {
                    String baseName = destFile.getName();
                    String ext = "";
                    int dotIndex = baseName.lastIndexOf(".");
                    
                    if (dotIndex > 0) {
                        ext = baseName.substring(dotIndex);
                        baseName = baseName.substring(0, dotIndex);
                    }
                    
                    String newFileName = baseName + "_" + System.currentTimeMillis() + ext;
                    destFile = new File(FTPServer.SERVER_DIR, newFileName);
                    fileName = newFileName; 
                    System.out.println(">> Phat hien trung ten, tu dong luu thanh: " + fileName);
                }
                
                FileOutputStream fos = new FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalRead = 0;
                
                while (totalRead < fileSize && (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }
                fos.close();
                
                System.out.println(">> Da luu thanh cong file cua [" + role + "] vao kho.");
                dos.writeUTF("Server: Da nhan thanh cong file " + fileName + " cua ban!");
                this.ghiLichSu(userInfo, "Tải lên tài liệu: " + fileName);
            } 
            // 2. XỬ LÝ LỆNH LIST
            else if (command.equals("LIST")) {
                File dir = new File(FTPServer.SERVER_DIR);
                File[] files = dir.listFiles();
                StringBuilder sb = new StringBuilder();
                if (files != null && files.length > 0) {
                    for (File f : files) {
                        sb.append("- ").append(f.getName()).append(" (").append(f.length()).append(" bytes)\n");
                    }
                } else {
                    sb.append("Kho luu tru hien dang trong!");
                }
                dos.writeUTF(sb.toString());
            }
            // 3. XỬ LÝ LỆNH DOWNLOAD
            else if (command.equals("DOWNLOAD")) {
                String fileName = dis.readUTF();
                File fileToDownload = new File(FTPServer.SERVER_DIR, new File(fileName).getName());

                if (fileToDownload.exists()) {
                    dos.writeUTF("OK"); 
                    dos.writeLong(fileToDownload.length()); 

                    FileInputStream fis = new FileInputStream(fileToDownload);
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                    fis.close();
                } else {
                    dos.writeUTF("ERROR"); 
                }
            }
            // 4. XỬ LÝ LỆNH GET_FILE_NAMES
            else if (command.equals("GET_FILE_NAMES")) {
                File dir = new File(FTPServer.SERVER_DIR);
                File[] files = dir.listFiles();
                StringBuilder sb = new StringBuilder();
                if (files != null && files.length > 0) {
                    for (int i = 0; i < files.length; i++) {
                        sb.append(files[i].getName());
                        if (i < files.length - 1) {
                            sb.append("|"); 
                        }
                    }
                }
                dos.writeUTF(sb.toString()); 
            }
            // 5. XỬ LÝ LỆNH DELETE
            else if (command.equals("DELETE")) {
                String token = dis.readUTF(); 
                if (token.equals("TEACHER_SECRET_TOKEN_999")) {
                    String fileName = dis.readUTF();
                    File fileToDelete = new File(FTPServer.SERVER_DIR, new File(fileName).getName());
                    
                    if (fileToDelete.exists()) {
                        if (fileToDelete.delete()) {
                            dos.writeUTF("Da xoa vinh vien file '" + fileName + "' khoi kho du lieu.");
                            this.ghiLichSu(userInfo, "Xóa vĩnh viễn tài liệu: " + fileName);
                        } else {
                            dos.writeUTF("Loi: Khong the xoa file nay.");
                        }
                    } else {
                        dos.writeUTF("Loi: Khong tim thay file tren Server!");
                    }
                } else {
                    dos.writeUTF("Loi: Ban khong co quyen xoa file!");
                }
            }
            // 6. XỬ LÝ LỆNH SEARCH
            else if (command.equals("SEARCH")) {
                String keyword = dis.readUTF().toLowerCase();
                File dir = new File(FTPServer.SERVER_DIR);
                File[] files = dir.listFiles();
                StringBuilder sb = new StringBuilder();
                boolean found = false;
                
                if (files != null && files.length > 0) {
                    for (File f : files) {
                        if (f.getName().toLowerCase().contains(keyword)) {
                            sb.append("- ").append(f.getName()).append(" (").append(f.length()).append(" bytes)\n");
                            found = true;
                        }
                    }
                }
                
                if (found) dos.writeUTF(sb.toString());
                else dos.writeUTF("Khong tim thay tai lieu nao phu hop!");
            }
            // 7. XỬ LÝ LỆNH MỞ ĐỌC FILE VĂN BẢN (READ_TEXT)
            else if (command.equals("READ_TEXT")) {
                String fileName = dis.readUTF();
                File f = new File(FTPServer.SERVER_DIR, new File(fileName).getName());
                if (f.exists() && f.isFile()) {
                    dos.writeUTF("OK");
                    byte[] encoded = java.nio.file.Files.readAllBytes(f.toPath());
                    dos.writeUTF(new String(encoded, "UTF-8"));
                } else {
                    dos.writeUTF("ERROR");
                }
            }
            // 8. XỬ LÝ LỆNH ĐỔI TÊN FILE (RENAME)
            else if (command.equals("RENAME")) {
                String oldName = dis.readUTF();
                String newName = dis.readUTF(); 
                
                File oldFile = new File(FTPServer.SERVER_DIR, new File(oldName).getName());
                File newFile = new File(FTPServer.SERVER_DIR, new File(newName).getName());
                
                if (oldFile.exists() && !newFile.exists()) {
                    if (oldFile.renameTo(newFile)) {
                        dos.writeUTF("Đã đổi tên tài liệu thành công: " + newName);
                        this.ghiLichSu(userInfo, "Đổi tên tài liệu '" + oldName + "' thành '" + newName + "'");
                    } else {
                        dos.writeUTF("Lỗi: Không thể thực hiện đổi tên lúc này!");
                    }
                } else if (newFile.exists()) {
                    dos.writeUTF("Lỗi: Tên file mới đã bị trùng!");
                } else {
                    dos.writeUTF("Lỗi: Không tìm thấy file gốc!");
                }
            }
            // 9. XỬ LÝ LỆNH LƯU FILE SAU KHI SỬA (SAVE_TEXT)
            else if (command.equals("SAVE_TEXT")) {
                String fileName = dis.readUTF();
                String newContent = dis.readUTF(); 
                
                File f = new File(FTPServer.SERVER_DIR, new File(fileName).getName());
                java.nio.file.Files.write(f.toPath(), newContent.getBytes("UTF-8"));
                
                dos.writeUTF("Đã lưu nội dung cập nhật lên Server thành công!");
                this.ghiLichSu(userInfo, "Chỉnh sửa nội dung tài liệu: " + fileName);
            }
            // 10. LỆNH XEM LỊCH SỬ THAO TÁC
            else if (command.equals("VIEW_HISTORY")) {
                StringBuilder sb = new StringBuilder();
                String dbURL = "jdbc:mysql://localhost:3306/MiniFTP_DB"; 
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(dbURL, "root", "")) {
                    String sql = "SELECT DATE_FORMAT(ThoiGian, '%d/%m/%Y %H:%i:%s') as TimeStr, DanhTinh, HanhDong FROM LichSu ORDER BY ThoiGian DESC";
                    java.sql.ResultSet rs = conn.createStatement().executeQuery(sql);
                    
                    while(rs.next()){
                        sb.append(rs.getString("TimeStr")).append("<col>")
                          .append(rs.getString("DanhTinh")).append("<col>")
                          .append(rs.getString("HanhDong")).append("<row>");
                    }
                } catch (Exception e) {
                    sb.append("Loi DB");
                }
                dos.writeUTF(sb.toString()); 
            }
            // 11. XỬ LÝ LỆNH ĐỌC ẢNH (READ_IMAGE) - MỚI THÊM
            else if (command.equals("READ_IMAGE")) {
                String fileName = dis.readUTF();
                System.out.println(">> [" + userInfo + "] dang mo xem anh: " + fileName);
                
                File f = new File(FTPServer.SERVER_DIR, new File(fileName).getName());
                if (f.exists() && f.isFile()) {
                    dos.writeUTF("OK");
                    byte[] imageBytes = java.nio.file.Files.readAllBytes(f.toPath());
                    dos.writeInt(imageBytes.length); // Gửi độ dài mảng byte
                    dos.write(imageBytes);           // Gửi dữ liệu ảnh
                    this.ghiLichSu(userInfo, "Xem ảnh: " + fileName);
                } else {
                    dos.writeUTF("ERROR");
                }
            }
            else {
                System.out.println(">> [" + userInfo + "] gui lenh la: " + command);
                dos.writeUTF("Loi: Lenh khong hop le tu phia Server!");
            }
            
        } catch (IOException e) {
            // Im lặng ngắt kết nối
        } 
    }
}