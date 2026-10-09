package com.example.maktabatisales;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

class Product {
    long id; String name, barcode; double buy, sell; int qty;
    Product(long id, String name, String barcode, double buy, double sell, int qty) {
        this.id=id; this.name=name; this.barcode=barcode; this.buy=buy; this.sell=sell; this.qty=qty;
    }
}
class Sale {
    long id; String date, customer, product; int qty; double total, paid;
}

class DBHelper extends SQLiteOpenHelper {
    DBHelper(Context c) { super(c, "maktabati.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,barcode TEXT,buy REAL NOT NULL,sell REAL NOT NULL,qty INTEGER NOT NULL DEFAULT 0)");
        d.execSQL("CREATE TABLE sales(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,customer TEXT,product TEXT,qty INTEGER,total REAL,paid REAL)");
        d.execSQL("CREATE TABLE people(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT,name TEXT,phone TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {}

    void addProduct(String name, String barcode, double buy, double sell, int qty) {
        ContentValues v=new ContentValues(); v.put("name",name); v.put("barcode",barcode);
        v.put("buy",buy); v.put("sell",sell); v.put("qty",qty);
        getWritableDatabase().insert("products",null,v);
    }
    ArrayList<Product> getProducts() { return queryProducts("SELECT * FROM products ORDER BY name", null); }
    ArrayList<Product> searchProducts(String q) {
        return queryProducts("SELECT * FROM products WHERE name LIKE ? OR barcode LIKE ? ORDER BY name",
            new String[]{"%"+q+"%","%"+q+"%"});
    }
    private ArrayList<Product> queryProducts(String sql, String[] args) {
        ArrayList<Product> a=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery(sql,args);
        try { while(c.moveToNext()) a.add(new Product(c.getLong(0),c.getString(1),c.getString(2),
            c.getDouble(3),c.getDouble(4),c.getInt(5))); } finally { c.close(); }
        return a;
    }
    Product findProduct(String q) {
        Cursor c=getReadableDatabase().rawQuery("SELECT * FROM products WHERE barcode=? OR name=? COLLATE NOCASE LIMIT 1",new String[]{q,q});
        try { if(!c.moveToFirst()) return null; return new Product(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getDouble(4),c.getInt(5)); }
        finally { c.close(); }
    }
    long createSale(String customer, Product p, int qty, double paid) {
        SQLiteDatabase d=getWritableDatabase(); d.beginTransaction();
        try {
            Cursor c=d.rawQuery("SELECT qty FROM products WHERE id=?",new String[]{String.valueOf(p.id)});
            int stock; try { if(!c.moveToFirst()) return -1; stock=c.getInt(0); } finally { c.close(); }
            if(qty<=0 || qty>stock || paid<0) return -1;
            double total=p.sell*qty;
            ContentValues s=new ContentValues();
            s.put("date",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault()).format(new java.util.Date()));
            s.put("customer",customer); s.put("product",p.name); s.put("qty",qty); s.put("total",total); s.put("paid",paid);
            long id=d.insert("sales",null,s);
            ContentValues stockV=new ContentValues(); stockV.put("qty",stock-qty);
            d.update("products",stockV,"id=?",new String[]{String.valueOf(p.id)});
            d.setTransactionSuccessful(); return id;
        } finally { d.endTransaction(); }
    }
    Sale getSale(long id) {
        Cursor c=getReadableDatabase().rawQuery("SELECT * FROM sales WHERE id=?",new String[]{String.valueOf(id)});
        try { if(!c.moveToFirst()) return null; Sale s=new Sale(); s.id=c.getLong(0); s.date=c.getString(1); s.customer=c.getString(2); s.product=c.getString(3); s.qty=c.getInt(4); s.total=c.getDouble(5); s.paid=c.getDouble(6); return s; }
        finally { c.close(); }
    }
    boolean addStock(String query, int qty) {
        Product p=findProduct(query); if(p==null || qty<=0) return false;
        ContentValues v=new ContentValues(); v.put("qty",p.qty+qty);
        return getWritableDatabase().update("products",v,"id=?",new String[]{String.valueOf(p.id)})>0;
    }
    void addPerson(String type,String name,String phone) {
        ContentValues v=new ContentValues(); v.put("type",type); v.put("name",name); v.put("phone",phone);
        getWritableDatabase().insert("people",null,v);
    }
    ArrayList<String> getPeople(String type) {
        ArrayList<String> a=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT name,phone FROM people WHERE type=? ORDER BY name",new String[]{type});
        try { while(c.moveToNext()) a.add(c.getString(0)+" | "+c.getString(1)); } finally { c.close(); } return a;
    }
    double salesSince(String date) {
        Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total),0) FROM sales WHERE date>=?",new String[]{date});
        try { c.moveToFirst(); return c.getDouble(0); } finally { c.close(); }
    }
}
