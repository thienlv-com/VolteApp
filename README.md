# VoLTE VN — Hướng dẫn sử dụng

Ứng dụng bật **VoLTE (gọi thoại 4G, HD Voice)** cho SIM Việt Nam trên điện thoại Android không hỗ trợ sẵn. Không cần root, không cần máy tính.

Đã thử trên OnePlus Nord N10 5G (BE2029, Android 11) với SIM Vinaphone và Viettel.

---

## Yêu cầu

- Android 11 trở lên
- Đã bật **Tuỳ chọn nhà phát triển**: vào *Cài đặt → Giới thiệu về điện thoại*, chạm 7 lần vào *Số bản dựng*
- Có **Wi-Fi** khi ghép nối lần đầu
- SIM của nhà mạng có hỗ trợ VoLTE và gói cước đã được mở VoLTE

## Cài đặt

1. Chép file `VoLTE-VN.apk` vào điện thoại. File đã có sẵn trong thư mục *Download*.
2. Mở file bằng trình quản lý file và chọn **Cài đặt**. Nếu máy hỏi, cho phép cài ứng dụng từ nguồn này.
3. Mở app **VoLTE VN** và cho phép quyền **Điện thoại** để app xem được trạng thái SIM.

## Ghép nối lần đầu (chỉ làm 1 lần)

1. Trong app, bấm **Ghép nối (làm 1 lần)**. App sẽ mở *Tuỳ chọn nhà phát triển*.
2. Bật **Gỡ lỗi không dây**. Nếu máy hỏi, chọn **Luôn cho phép trên mạng này**.
3. Chạm vào chữ *Gỡ lỗi không dây*, rồi chọn **Ghép nối thiết bị bằng mã ghép nối**. Màn hình sẽ hiện một mã 6 số.
4. Kéo thanh thông báo xuống. Trong thông báo của **VoLTE VN**, bấm **Nhập mã**, gõ mã 6 số rồi gửi.
5. Chờ khoảng 30 giây. App tự bật VoLTE và báo kết quả bằng thông báo.

Xong bước này, bạn có thể tắt *Gỡ lỗi không dây*. Khi cần, app sẽ tự bật lên rồi tắt lại.

## Sử dụng hằng ngày

Bạn **không cần làm gì**. VoLTE được giữ nguyên kể cả sau khi khởi động lại máy.

Màn hình app gồm:

| Mục | Ý nghĩa |
|---|---|
| ✅ VoLTE đã bật / đang hoạt động | SIM đang gọi được qua 4G |
| ⏳ Đã bật, đang chờ mạng đăng ký | Chờ 20–40 giây. Vinaphone thường chậm hơn Viettel |
| ❌ VoLTE chưa bật | Bấm **Bật VoLTE ngay** |
| **Bật VoLTE ngay** | Bật lại VoLTE cho tất cả SIM. Cần có Wi-Fi |
| **Khôi phục mặc định** | Tắt VoLTE, đưa máy về cấu hình gốc |
| **Ghép nối lại** | Dùng khi app báo khoá ghép nối không còn hợp lệ |

**Khi nào cần bấm "Bật VoLTE ngay":** sau khi tháo/lắp hoặc đổi SIM, sau khi cập nhật hệ điều hành, hoặc khi thấy ❌.

## Kiểm tra VoLTE có hoạt động thật không

- Gọi một cuộc. Nếu trong lúc gọi, biểu tượng sóng vẫn là **4G/LTE**, không tụt xuống H/3G, thì VoLTE đang hoạt động.
- Hoặc mở *Cài đặt → Mạng di động → chọn SIM*, xem công tắc **VoLTE / Gọi nâng cao 4G** đã bật chưa.

## Xử lý sự cố

| Hiện tượng | Cách xử lý |
|---|---|
| "Cần Wi-Fi để bật VoLTE" | Kết nối Wi-Fi rồi bấm **Bật VoLTE ngay** |
| "Không kết nối được Gỡ lỗi không dây" | Bật thủ công *Gỡ lỗi không dây*, chọn *Luôn cho phép trên mạng này*, rồi thử lại |
| "Khoá ghép đôi không còn hợp lệ" | Bấm **Ghép nối lại** và làm lại phần *Ghép nối lần đầu* |
| Không thấy thông báo "Nhập mã" | Cho phép app gửi thông báo: *Cài đặt → Ứng dụng → VoLTE VN → Thông báo* |
| Mã ghép nối sai hoặc hết hạn | Bấm **Ghép nối** trong app, lấy mã mới rồi nhập nhanh hơn |
| SIM vẫn ⏳ quá 2 phút | Bật/tắt Chế độ máy bay một lần. Nếu vẫn vậy, có thể nhà mạng chưa mở VoLTE cho thuê bao của bạn (gọi tổng đài để hỏi) |

## Gỡ bỏ

1. Trong app, bấm **Khôi phục mặc định**.
2. Gỡ cài đặt app như bình thường.
3. Tuỳ chọn: vào *Gỡ lỗi không dây → Thiết bị đã ghép nối*, xoá mục **VoLTE VN**.

## An toàn & quyền riêng tư

- App không dùng Internet ngoài việc kết nối vào **chính điện thoại** qua Gỡ lỗi không dây. App không gửi dữ liệu ra ngoài.
- App chỉ thay đổi cấu hình nhà mạng liên quan đến VoLTE. Không sửa hệ thống, không cần root, không ảnh hưởng bảo hành.
- Bấm **Khôi phục mặc định** là máy trở về cấu hình gốc.
- Khoá ghép nối cho phép app tự kết nối Gỡ lỗi không dây trên máy. Bạn có thể thu hồi bất cứ lúc nào trong *Gỡ lỗi không dây → Thiết bị đã ghép nối*.

---

*Ghi chú kỹ thuật cho người phát triển: xem `.claude/MEMORY.md`.*
