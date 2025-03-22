import sys
import cv2
import tkinter as tk
from tkinter import filedialog, messagebox
from PIL import Image, ImageTk
import qrcode

class QRCodeApp:
    def __init__(self, root):
        self.root = root
        self.root.title("QR Code Generator & Reader")
        self.root.geometry("400x550")

        self.label = tk.Label(root, text="QR Code Generator & Reader", font=("Arial", 14))
        self.label.pack(pady=10)

        self.entry = tk.Entry(root, width=40)
        self.entry.pack(pady=5)

        self.generate_button = tk.Button(root, text="Generate QR Code", command=self.generate_qr)
        self.generate_button.pack(pady=5)

        self.save_button = tk.Button(root, text="Save QR Code", command=self.save_qr)
        self.save_button.pack(pady=5)
        self.save_button.pack_forget()  # Esconde o botão até o QR ser gerado

        self.camera_button = tk.Button(root, text="Read QR Code from Camera", command=self.read_qr_from_camera)
        self.camera_button.pack(pady=5)

        self.qr_label = tk.Label(root)
        self.qr_label.pack(pady=10)

        self.qr_image = None  # Para armazenar o QR Code gerado

    def generate_qr(self):
        data = self.entry.get()
        if not data:
            messagebox.showerror("Error", "Please enter some text to generate a QR Code!")
            return

        qr = qrcode.QRCode(
            version=1,
            error_correction=qrcode.constants.ERROR_CORRECT_L,
            box_size=10,
            border=4,
        )
        qr.add_data(data)
        qr.make(fit=True)
        img = qr.make_image(fill="black", back_color="white")

        self.qr_image = img  # Armazena a imagem gerada

        img_tk = ImageTk.PhotoImage(img)
        self.qr_label.config(image=img_tk)
        self.qr_label.image = img_tk

        self.save_button.pack(pady=5)  # Mostra o botão "Save QR Code"

    def save_qr(self):
        if self.qr_image is None:
            messagebox.showerror("Error", "No QR Code to save!")
            return

        file_path = filedialog.asksaveasfilename(
            defaultextension=".png",
            filetypes=[("PNG files", "*.png"), ("All Files", "*.*")]
        )

        if not file_path:
            return  # Usuário cancelou a ação

        self.qr_image.save(file_path)
        messagebox.showinfo("Success", f"QR Code saved as {file_path}")

    
    def read_qr_from_camera(self):
        cap = cv2.VideoCapture(0)
        detector = cv2.QRCodeDetector()

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            data, bbox, _ = detector.detectAndDecode(frame)

            if bbox is not None and data:
                messagebox.showinfo("QR Code Data", f"QR Code: {data}")
                cap.release()
                cv2.destroyAllWindows()
                return

            cv2.imshow("QR Code Scanner - Press 'q' to exit", frame)

            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

        cap.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    root = tk.Tk()
    app = QRCodeApp(root)
    root.mainloop()
