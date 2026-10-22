package com.example.myapplication.model;

/**
 * 紧急联系人（ContactOut / ContactIn）
 * 数据字典：id / name / phone / relation / priority / is_primary
 * 保存走整体替换（PUT /elders/{id}/contacts）；is_primary 最多一个为 true。
 */
public class Contact {

    private long id;
    private String name;
    private String phone;
    private String relation;
    private int priority = 1;
    private boolean primary;

    public Contact() {
    }

    public Contact(long id, String name, String phone, String relation, int priority, boolean primary) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.relation = relation;
        this.priority = priority;
        this.primary = primary;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
}
