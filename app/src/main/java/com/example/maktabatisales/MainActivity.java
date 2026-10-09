package com.example.maktabatisales;

import android.app.*;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Starter app for an offline bookstore sales system.
 * Uses SQLiteOpenHelper; all business data stays on the device.
 */
public class MainActivity extends Activity {
    private DBHelper db;
    private LinearLayout root;
    private final String currency = "ر.ي";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper(this);
        showHome();
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setPadding(12, 10, 12, 10);
        t.setTextColor(0xFF172033);
        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void base(String title) {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 20);
        scroll.addView(root);
        TextView heading = text(title, 24); heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        root.addView(button("الرئيسية", this::showHome));
        setContentView(scroll);
    }

    private void showHome() {
        base("مبيعات مكتبتي");
        root.addView(text("نظام مبيعات ومخزون يعمل دون إنترنت", 16));
        root.addView(button("الأصناف والمخزون", this::showProducts));
        root.addView(button("فاتورة بيع جديدة", this::newSale));
        root.addView(button("المشتريات", this::newPurchase));
        root.addView(button("العملاء", () -> showPeople("customers")));
        root.addView(button("الموردون", () -> showPeople("suppliers")));
        root.addView(button("التقارير", this::showReports));
        root.addView(button("نسخ احتياطي", this::backupInfo));
        root.addView(text("ملاحظة: هذه نسخة تأسيسية؛ تُضاف إدارة المرتجعات والصلاحيات والجرد المتقدم في مراحل التطوير التالية.", 13));
    }

    private void showProducts() {
        base("الأصناف والمخزون");
        EditText name = field("اسم الصنف");
        EditText barcode = field("الباركود (اختياري)");
        EditText buy = field("سعر الشراء");
        EditText sell = field("سعر البيع");
        EditText qty = field("الكمية الحالية");
        root.addView(name); root.addView(barcode); root.addView(buy); root.addView(sell); root.addView(qty);
        root.addView(button("حفظ الصنف", () -> {
            try {
                if (name.getText().toString().trim().isEmpty()) { msg("أدخل اسم الصنف"); return; }
                db.addProduct(name.getText().toString().trim(), barcode.getText().toString().trim(),
                    Double.parseDouble(buy.getText().toString()), Double.parseDouble(sell.getText().toString()),
                    Integer.parseInt(qty.getText().toString()));
                msg("تم حفظ الصنف"); showProducts();
            } catch (Exception e) { msg("تحقق من الأسعار والكمية"); }
        }));
        EditText search = field("ابحث بالاسم أو الباركود");
        root.addView(search);
        root.addView(button("بحث", () -> renderProducts(db.searchProducts(search.getText().toString().trim()))));
        renderProducts(db.getProducts());
    }

    private void renderProducts(ArrayList<Product> products) {
        for (Product p : products) {
            TextView row = text(p.name + " | " + p.qty + " قطعة | بيع: " + p.sell + " " + currency
                + (p.barcode.isEmpty() ? "" : " | " + p.barcode), 15);
            row.setBackgroundColor(0xFFF0F4F8); root.addView(row);
        }
    }

    private void newSale() {
        ArrayList<Product> products = db.getProducts();
        if (products.isEmpty()) { msg("أضف أصنافًا أولًا"); return; }
        base("فاتورة بيع جديدة");
        EditText customer = field("اسم العميل (اختياري)");
        EditText query = field("اسم الصنف أو الباركود");
        EditText qty = field("الكمية");
        EditText paid = field("المبلغ المدفوع");
        root.addView(customer); root.addView(query); root.addView(qty); root.addView(paid);
        root.addView(button("إتمام البيع", () -> {
            Product p = db.findProduct(query.getText().toString().trim());
            if (p == null) { msg("الصنف غير موجود"); return; }
            try {
                int count = Integer.parseInt(qty.getText().toString());
                double payment = Double.parseDouble(paid.getText().toString());
                if (count <= 0 || count > p.qty) { msg("الكمية غير صحيحة أو المخزون غير كافٍ"); return; }
                long invoiceId = db.createSale(customer.getText().toString().trim(), p, count, payment);
                if (invoiceId > 0) showInvoice(invoiceId);
                else msg("تعذر تسجيل الفاتورة");
            } catch (Exception e) { msg("تحقق من الكمية والمبلغ المدفوع"); }
        }));
        root.addView(text("هذه النسخة الأولية تسجل صنفًا واحدًا في الفاتورة. دعم سلة متعددة الأصناف ضمن التطوير التالي.", 13));
    }

    private void showInvoice(long id) {
        Sale s = db.getSale(id);
        base("فاتورة بيع #" + id);
        root.addView(text("التاريخ: " + s.date, 16));
        root.addView(text("العميل: " + s.customer, 16));
        root.addView(text("الصنف: " + s.product, 16));
        root.addView(text("الكمية: " + s.qty, 16));
        root.addView(text("الإجمالي: " + s.total + " " + currency, 18));
        root.addView(text("المدفوع: " + s.paid + " " + currency, 16));
        root.addView(text("المتبقي: " + (s.total - s.paid) + " " + currency, 16));
        root.addView(button("طباعة / حفظ PDF", () -> printInvoice(s)));
    }

    private void printInvoice(Sale s) {
        android.print.PrintManager pm = (android.print.PrintManager)getSystemService(PRINT_SERVICE);
        android.print.PrintDocumentAdapter adapter = new android.print.PrintDocumentAdapter() {
            @Override public void onLayout(android.print.PrintAttributes oldA, android.print.PrintAttributes newA,
                    android.os.CancellationSignal cancel, LayoutResultCallback cb, android.os.Bundle extras) {
                if (cancel.isCanceled()) { cb.onLayoutCancelled(); return; }
                android.print.PrintDocumentInfo info = new android.print.PrintDocumentInfo.Builder("invoice-" + s.id + ".pdf")
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build();
                cb.onLayoutFinished(info, true);
            }
            @Override public void onWrite(PageRange[] pages, android.os.ParcelFileDescriptor dest,
                    android.os.CancellationSignal cancel, WriteResultCallback cb) {
                android.graphics.pdf.PdfDocument pdf = new android.graphics.pdf.PdfDocument();
                android.graphics.pdf.PdfDocument.PageInfo pi = new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create();
                android.graphics.pdf.PdfDocument.Page page = pdf.startPage(pi);
                android.graphics.Canvas c = page.getCanvas();
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setTextSize(18); c.drawText("Sales Invoice #" + s.id, 40, 60, paint);
                paint.setTextSize(13);
                c.drawText("Date: " + s.date, 40, 100, paint);
                c.drawText("Customer: " + s.customer, 40, 130, paint);
                c.drawText("Item: " + s.product, 40, 180, paint);
                c.drawText("Quantity: " + s.qty, 40, 210, paint);
                c.drawText("Total: " + s.total + " YER", 40, 260, paint);
                c.drawText("Paid: " + s.paid + " YER", 40, 290, paint);
                c.drawText("Balance: " + (s.total-s.paid) + " YER", 40, 320, paint);
                pdf.finishPage(page);
                try { pdf.writeTo(new java.io.FileOutputStream(dest.getFileDescriptor())); cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES}); }
                catch (Exception e) { cb.onWriteFailed(e.toString()); }
                finally { pdf.close(); }
            }
        };
        pm.print("Invoice-" + s.id, adapter, new android.print.PrintAttributes.Builder()
            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4).build());
    }

    private void newPurchase() {
        base("تسجيل مشتريات");
        EditText item = field("اسم الصنف أو الباركود الموجود");
        EditText qty = field("الكمية المشتراة");
        root.addView(item); root.addView(qty);
        root.addView(button("إضافة للمخزون", () -> {
            try {
                int n = Integer.parseInt(qty.getText().toString());
                if (n <= 0) { msg("أدخل كمية صحيحة"); return; }
                if (db.addStock(item.getText().toString().trim(), n)) { msg("تمت إضافة الكمية"); showProducts(); }
                else msg("الصنف غير موجود؛ أضفه أولًا من شاشة الأصناف");
            } catch (Exception e) { msg("تحقق من الكمية"); }
        }));
        root.addView(text("هذه شاشة إضافة كمية لصنف مسجل. تسجيل فواتير المورد وتكلفة الشراء التفصيلية يضاف في المرحلة التالية.", 13));
    }

    private void showPeople(String table) {
        base(table.equals("customers") ? "العملاء" : "الموردون");
        EditText name = field("الاسم");
        EditText phone = field("رقم الهاتف");
        root.addView(name); root.addView(phone);
        root.addView(button("حفظ", () -> {
            if (name.getText().toString().trim().isEmpty()) { msg("أدخل الاسم"); return; }
            db.addPerson(table, name.getText().toString().trim(), phone.getText().toString().trim());
            msg("تم الحفظ");
        }));
        for (String p : db.getPeople(table)) root.addView(text(p, 15));
    }

    private void showReports() {
        base("التقارير");
        double today = db.salesSince(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        double all = db.salesSince("0000-00-00");
        root.addView(text("مبيعات اليوم: " + today + " " + currency, 18));
        root.addView(text("إجمالي المبيعات المسجلة: " + all + " " + currency, 18));
        root.addView(text("التقارير الحالية إجمالية وأولية؛ يلزم تطوير تصفية شهرية وسنوية وصافي الربح بعد احتساب تكلفة البضاعة والمرتجعات.", 13));
    }

    private void backupInfo() {
        new AlertDialog.Builder(this).setTitle("النسخ الاحتياطي")
            .setMessage("قاعدة البيانات محفوظة داخل التطبيق. هذه النسخة تعرض التأسيس فقط؛ ستضاف وظيفة تصدير واستعادة ملف النسخة الاحتياطية في المرحلة التالية.")
            .setPositiveButton("حسنًا", null).show();
    }

    private EditText field(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true);
        return e;
    }
    private void msg(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
