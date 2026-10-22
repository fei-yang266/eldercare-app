package com.example.myapplication.model;

/**
 * 老人档案（ElderOut）
 * 数据字典：id / name / gender / birth_date / height_cm / weight_kg / address / notes / phone
 * phone：新建必填、全局唯一（^1[3-9]\d{9}$）；存量档案可能为 null。
 */
public class Elder {

    private long id;
    private String name;
    private String gender;
    private String birthDate;
    private Integer heightCm;
    private Double weightKg;
    private String address;
    private String notes;
    private String phone;

    public Elder() {
    }

    public Elder(long id, String name, String gender, String birthDate,
                 Integer heightCm, Double weightKg, String address, String notes) {
        this.id = id;
        this.name = name;
        this.gender = gender;
        this.birthDate = birthDate;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.address = address;
        this.notes = notes;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Integer heightCm) {
        this.heightCm = heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
