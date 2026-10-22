package com.example.myapplication.model;

/**
 * 电子围栏（GeofenceOut）
 * 数据字典：id / name / center_lat / center_lon / radius_m / enabled
 * family 可读写（GET/PUT /geofences/{elder_id}）；App 首版只读展示。
 */
public class Geofence {

    private long id;
    private String name;
    private double centerLat;
    private double centerLon;
    private int radiusM;
    private boolean enabled;

    public Geofence() {
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

    public double getCenterLat() {
        return centerLat;
    }

    public void setCenterLat(double centerLat) {
        this.centerLat = centerLat;
    }

    public double getCenterLon() {
        return centerLon;
    }

    public void setCenterLon(double centerLon) {
        this.centerLon = centerLon;
    }

    public int getRadiusM() {
        return radiusM;
    }

    public void setRadiusM(int radiusM) {
        this.radiusM = radiusM;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
