import sys
import cv2
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from PIL import Image, ImageTk
import qrcode

class QRCodeApp:
    def __init__(self, root):
        self.root = root
        self.root.title("QR Code Generator & Reader")
        self.root.geometry("450x650")

        self.label = tk.Label(root, text="QR Code Generator & Reader", font=("Arial", 14))
        self.label.pack(pady=10)

        self.type_var = tk.StringVar()
        self.type_var.set("Plain Text")
        self.type_menu = ttk.Combobox(root, textvariable=self.type_var, values=[
            "Plain Text", "URL", "Email", "Phone", "SMS", "Geo Location",
            "Calendar Event", "Contact", "Wi-Fi"
        ], state="readonly")
        self.type_menu.pack(pady=5)
        self.type_menu.bind("<<ComboboxSelected>>", self.update_fields)

        self.fields_frame = tk.Frame(root)
        self.fields_frame.pack(pady=5)

        self.entries = {}
        self.update_fields()

        self.generate_button = tk.Button(root, text="Generate QR Code", command=self.generate_qr)
        self.generate_button.pack(pady=5)

        self.save_button = tk.Button(root, text="Save QR Code", command=self.save_qr)
        self.save_button.pack(pady=5)
        self.save_button.pack_forget()

        self.camera_button = tk.Button(root, text="Read QR Code from Camera", command=self.read_qr_from_camera)
        self.camera_button.pack(pady=5)

        self.result_label = tk.Label(root, text="QR Code Result:")
        self.result_label.pack(pady=(10, 0))

        result_frame = tk.Frame(root)
        result_frame.pack(pady=(0, 10))

        self.result_scrollbar = tk.Scrollbar(result_frame)
        self.result_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        self.result_text = tk.Text(result_frame, width=45, height=5, wrap=tk.WORD, yscrollcommand=self.result_scrollbar.set)
        self.result_text.pack(side=tk.LEFT, fill=tk.BOTH)
        self.result_text.config(state='disabled')

        self.result_scrollbar.config(command=self.result_text.yview)

        self.qr_label = tk.Label(root)
        self.qr_label.pack(pady=10)

        self.qr_image = None
        

    def update_fields(self, event=None):
        for widget in self.fields_frame.winfo_children():
            widget.destroy()
        self.entries.clear()

        field_sets = {
            "Plain Text": ["Text"],
            "URL": ["URL"],
            "Email": ["Email"],
            "Phone": ["Phone Number"],
            "SMS": ["Phone Number", "Message"],
            "Geo Location": ["Latitude", "Longitude"],
            "Calendar Event": ["Title", "Start (YYYYMMDDTHHMMSS)", "End (YYYYMMDDTHHMMSS)"],
            "Contact": ["Name", "Phone", "Email"],
            "Wi-Fi": ["SSID", "Password", "Encryption (WPA/WEP/nopass)"]
        }

        for field in field_sets[self.type_var.get()]:
            lbl = tk.Label(self.fields_frame, text=field)
            lbl.pack()
            ent = tk.Entry(self.fields_frame, width=40)
            ent.pack(pady=2)
            self.entries[field] = ent

    def get_qr_data(self):
        t = self.type_var.get()
        e = self.entries
        try:
            if t == "Plain Text":
                return e["Text"].get()
            elif t == "URL":
                return e["URL"].get()
            elif t == "Email":
                return f"mailto:{e['Email'].get()}"
            elif t == "Phone":
                return f"tel:{e['Phone Number'].get()}"
            elif t == "SMS":
                return f"SMSTO:{e['Phone Number'].get()}:{e['Message'].get()}"
            elif t == "Geo Location":
                return f"geo:{e['Latitude'].get()},{e['Longitude'].get()}"
            elif t == "Calendar Event":
                return f"BEGIN:VEVENT\nSUMMARY:{e['Title'].get()}\nDTSTART:{e['Start (YYYYMMDDTHHMMSS)'].get()}\nDTEND:{e['End (YYYYMMDDTHHMMSS)'].get()}\nEND:VEVENT"
            elif t == "Contact":
                return f"MECARD:N:{e['Name'].get()};TEL:{e['Phone'].get()};EMAIL:{e['Email'].get()};"
            elif t == "Wi-Fi":
                return f"WIFI:T:{e['Encryption (WPA/WEP/nopass)'].get()};S:{e['SSID'].get()};P:{e['Password'].get()};;"
        except KeyError:
            return ""

    def generate_qr(self):
        data = self.get_qr_data()
        if not data:
            messagebox.showerror("Error", "Please fill in all fields to generate a QR Code!")
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

        self.qr_image = img

        img_tk = ImageTk.PhotoImage(img)
        self.qr_label.config(image=img_tk)
        self.qr_label.image = img_tk

        self.save_button.pack(pady=5)

    def save_qr(self):
        if self.qr_image is None:
            messagebox.showerror("Error", "No QR Code to save!")
            return

        file_path = filedialog.asksaveasfilename(
            defaultextension=".png",
            filetypes=[("PNG files", "*.png"), ("All Files", "*.*")]
        )

        if not file_path:
            return

        self.qr_image.save(file_path)
        messagebox.showinfo("Success", f"QR Code saved as {file_path}")

    def set_result_text(self, text):
        self.result_text.config(state='normal')
        self.result_text.delete("1.0", tk.END)
        self.result_text.insert(tk.END, text)
        self.result_text.config(state='disabled')


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
                self.set_result_text(data)
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
