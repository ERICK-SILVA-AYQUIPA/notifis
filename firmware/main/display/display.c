#include "display.h"

#include <stdio.h>
#include <string.h>

#include "driver/gpio.h"
#include "driver/spi_master.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_panel_st7789.h"
#include "esp_log.h"
#include "esp_lvgl_port.h"
#include "sdkconfig.h"

static const char *TAG = "display";

#define COLOR_BG 0x0B1F17
#define COLOR_BRAND 0x0B8F5A
#define COLOR_OK 0x3FD18F
#define COLOR_WARN 0xF2A33A
#define COLOR_ERR 0xE5534B
#define COLOR_TEXT 0xFFFFFF
#define COLOR_MUTED 0x9FB3AA

static lv_obj_t *s_status_conn, *s_status_batt, *s_status_vol;
static lv_obj_t *s_waiting, *s_payment, *s_error;
static lv_obj_t *s_amount, *s_source, *s_sender, *s_meta, *s_error_text, *s_last;
static lv_obj_t *s_passkey_box, *s_passkey_label;

static lv_obj_t *label(lv_obj_t *parent, const lv_font_t *font, uint32_t color)
{
    lv_obj_t *l = lv_label_create(parent);
    lv_obj_set_style_text_font(l, font, 0);
    lv_obj_set_style_text_color(l, lv_color_hex(color), 0);
    lv_label_set_long_mode(l, LV_LABEL_LONG_DOT);
    lv_obj_set_width(l, CONFIG_PB_LCD_WIDTH - 16);
    lv_obj_set_style_text_align(l, LV_TEXT_ALIGN_CENTER, 0);
    return l;
}

static lv_obj_t *panel(lv_obj_t *parent)
{
    lv_obj_t *p = lv_obj_create(parent);
    lv_obj_remove_style_all(p);
    lv_obj_set_size(p, CONFIG_PB_LCD_WIDTH, CONFIG_PB_LCD_HEIGHT - 28);
    lv_obj_align(p, LV_ALIGN_BOTTOM_MID, 0, 0);
    lv_obj_set_flex_flow(p, LV_FLEX_FLOW_COLUMN);
    lv_obj_set_flex_align(p, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
    lv_obj_set_style_pad_row(p, 6, 0);
    return p;
}

static void build_ui(void)
{
    lv_obj_t *scr = lv_screen_active();
    lv_obj_set_style_bg_color(scr, lv_color_hex(COLOR_BG), 0);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);

    /* Barra de estado */
    lv_obj_t *bar = lv_obj_create(scr);
    lv_obj_remove_style_all(bar);
    lv_obj_set_size(bar, CONFIG_PB_LCD_WIDTH, 28);
    lv_obj_set_style_bg_color(bar, lv_color_hex(COLOR_BRAND), 0);
    lv_obj_set_style_bg_opa(bar, LV_OPA_COVER, 0);
    lv_obj_align(bar, LV_ALIGN_TOP_MID, 0, 0);
    s_status_conn = lv_label_create(bar);
    s_status_batt = lv_label_create(bar);
    s_status_vol = lv_label_create(bar);
    lv_obj_t *items[] = {s_status_conn, s_status_batt, s_status_vol};
    for (int i = 0; i < 3; i++) {
        lv_obj_set_style_text_color(items[i], lv_color_hex(COLOR_TEXT), 0);
        lv_obj_set_style_text_font(items[i], &lv_font_montserrat_14, 0);
    }
    lv_obj_align(s_status_conn, LV_ALIGN_LEFT_MID, 6, 0);
    lv_obj_align(s_status_vol, LV_ALIGN_CENTER, 0, 0);
    lv_obj_align(s_status_batt, LV_ALIGN_RIGHT_MID, -6, 0);

    /* Esperando pagos */
    s_waiting = panel(scr);
    lv_label_set_text(label(s_waiting, &lv_font_montserrat_28, COLOR_TEXT), "PAGOBOX");
    lv_label_set_text(label(s_waiting, &lv_font_montserrat_20, COLOR_OK), "Esperando pagos");
    s_last = label(s_waiting, &lv_font_montserrat_14, COLOR_MUTED);
    lv_label_set_text(s_last, "");

    /* Nuevo pago */
    s_payment = panel(scr);
    s_source = label(s_payment, &lv_font_montserrat_20, COLOR_OK);
    s_amount = label(s_payment, &lv_font_montserrat_40, COLOR_TEXT);
    s_sender = label(s_payment, &lv_font_montserrat_20, COLOR_TEXT);
    s_meta = label(s_payment, &lv_font_montserrat_14, COLOR_MUTED);

    /* Desconectado o con errores */
    s_error = panel(scr);
    lv_label_set_text(label(s_error, &lv_font_montserrat_28, COLOR_ERR), LV_SYMBOL_WARNING);
    lv_label_set_text(label(s_error, &lv_font_montserrat_20, COLOR_TEXT), "Sin conexion");
    s_error_text = label(s_error, &lv_font_montserrat_14, COLOR_MUTED);
    lv_label_set_long_mode(s_error_text, LV_LABEL_LONG_WRAP);

    /* Código de vinculación */
    s_passkey_box = lv_obj_create(scr);
    lv_obj_set_size(s_passkey_box, CONFIG_PB_LCD_WIDTH - 24, 110);
    lv_obj_center(s_passkey_box);
    lv_obj_set_style_bg_color(s_passkey_box, lv_color_hex(COLOR_TEXT), 0);
    lv_obj_t *t = lv_label_create(s_passkey_box);
    lv_obj_set_style_text_color(t, lv_color_hex(COLOR_BG), 0);
    lv_label_set_text(t, "Codigo de vinculacion");
    lv_obj_align(t, LV_ALIGN_TOP_MID, 0, 0);
    s_passkey_label = lv_label_create(s_passkey_box);
    lv_obj_set_style_text_font(s_passkey_label, &lv_font_montserrat_40, 0);
    lv_obj_set_style_text_color(s_passkey_label, lv_color_hex(COLOR_BG), 0);
    lv_obj_align(s_passkey_label, LV_ALIGN_BOTTOM_MID, 0, 0);
    lv_obj_add_flag(s_passkey_box, LV_OBJ_FLAG_HIDDEN);
}

esp_err_t pb_display_hw_init(void)
{
    gpio_config_t bl = {.pin_bit_mask = 1ULL << CONFIG_PB_LCD_PIN_BL, .mode = GPIO_MODE_OUTPUT};
    gpio_config(&bl);
    gpio_set_level(CONFIG_PB_LCD_PIN_BL, 0);

    spi_bus_config_t bus = {
        .sclk_io_num = CONFIG_PB_LCD_PIN_SCLK,
        .mosi_io_num = CONFIG_PB_LCD_PIN_MOSI,
        .miso_io_num = -1,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
        .max_transfer_sz = CONFIG_PB_LCD_WIDTH * 40 * sizeof(uint16_t),
    };
    ESP_ERROR_CHECK(spi_bus_initialize(SPI2_HOST, &bus, SPI_DMA_CH_AUTO));

    esp_lcd_panel_io_handle_t io = NULL;
    esp_lcd_panel_io_spi_config_t io_cfg = {
        .dc_gpio_num = CONFIG_PB_LCD_PIN_DC,
        .cs_gpio_num = CONFIG_PB_LCD_PIN_CS,
        .pclk_hz = 40 * 1000 * 1000,
        .lcd_cmd_bits = 8,
        .lcd_param_bits = 8,
        .spi_mode = 0,
        .trans_queue_depth = 10,
    };
    ESP_ERROR_CHECK(esp_lcd_new_panel_io_spi((esp_lcd_spi_bus_handle_t)SPI2_HOST, &io_cfg, &io));

    esp_lcd_panel_handle_t panel_handle = NULL;
    esp_lcd_panel_dev_config_t panel_cfg = {
        .reset_gpio_num = CONFIG_PB_LCD_PIN_RST,
        .rgb_ele_order = LCD_RGB_ELEMENT_ORDER_RGB,
        .bits_per_pixel = 16,
    };
    ESP_ERROR_CHECK(esp_lcd_new_panel_st7789(io, &panel_cfg, &panel_handle));
    ESP_ERROR_CHECK(esp_lcd_panel_reset(panel_handle));
    ESP_ERROR_CHECK(esp_lcd_panel_init(panel_handle));
    ESP_ERROR_CHECK(esp_lcd_panel_invert_color(panel_handle, true));
    ESP_ERROR_CHECK(esp_lcd_panel_disp_on_off(panel_handle, true));

    const lvgl_port_cfg_t lvgl_cfg = ESP_LVGL_PORT_INIT_CONFIG();
    ESP_ERROR_CHECK(lvgl_port_init(&lvgl_cfg));
    const lvgl_port_display_cfg_t disp_cfg = {
        .io_handle = io,
        .panel_handle = panel_handle,
        .buffer_size = CONFIG_PB_LCD_WIDTH * 40,
        .double_buffer = true,
        .hres = CONFIG_PB_LCD_WIDTH,
        .vres = CONFIG_PB_LCD_HEIGHT,
        .monochrome = false,
        .rotation = {.swap_xy = false, .mirror_x = false, .mirror_y = false},
        .flags = {.buff_dma = true, .swap_bytes = true},
    };
    if (lvgl_port_add_disp(&disp_cfg) == NULL) {
        return ESP_FAIL;
    }
    if (lvgl_port_lock(0)) {
        build_ui();
        lvgl_port_unlock();
    }
    gpio_set_level(CONFIG_PB_LCD_PIN_BL, 1);
    ESP_LOGI(TAG, "Pantalla %dx%d lista", CONFIG_PB_LCD_WIDTH, CONFIG_PB_LCD_HEIGHT);
    return ESP_OK;
}

static void show(lv_obj_t *obj, bool visible)
{
    if (visible) {
        lv_obj_remove_flag(obj, LV_OBJ_FLAG_HIDDEN);
    } else {
        lv_obj_add_flag(obj, LV_OBJ_FLAG_HIDDEN);
    }
}

void pb_display_render(const pb_display_view_t *v)
{
    if (!lvgl_port_lock(100)) {
        return;
    }
    lv_label_set_text(s_status_conn, v->android_connected ? (v->wifi_connected ? LV_SYMBOL_BLUETOOTH " " LV_SYMBOL_WIFI : LV_SYMBOL_BLUETOOTH)
                                                          : LV_SYMBOL_CLOSE " Celular");
    if (v->muted) {
        lv_label_set_text(s_status_vol, LV_SYMBOL_MUTE);
    } else {
        lv_label_set_text_fmt(s_status_vol, LV_SYMBOL_VOLUME_MAX " %d", v->volume);
    }
    if (v->battery < 0) {
        lv_label_set_text(s_status_batt, v->charging ? LV_SYMBOL_CHARGE : "");
    } else {
        const char *icon = v->battery > 75 ? LV_SYMBOL_BATTERY_FULL : v->battery > 50 ? LV_SYMBOL_BATTERY_3
                         : v->battery > 25 ? LV_SYMBOL_BATTERY_2 : v->battery > 10 ? LV_SYMBOL_BATTERY_1 : LV_SYMBOL_BATTERY_EMPTY;
        lv_label_set_text_fmt(s_status_batt, "%s%s %d%%", v->charging ? LV_SYMBOL_CHARGE : "", icon, v->battery);
    }
    show(s_waiting, v->screen == PB_SCREEN_WAITING);
    show(s_payment, v->screen == PB_SCREEN_NEW_PAYMENT);
    show(s_error, v->screen == PB_SCREEN_DISCONNECTED);
    lv_label_set_text(s_error_text, v->error != NULL ? v->error : "Abre PAGOBOX en el celular y acercalo al equipo");
    lvgl_port_unlock();
}

void pb_display_set_payment(const pb_payment_event_t *evt)
{
    const char *time_hhmm = evt->time_hhmm[0] != '\0' ? evt->time_hhmm : "--:--";
    char amount[40], sender[PB_SENDER_MAX], source[PB_SOURCE_MAX];
    pb_format_amount(evt->amount_cents, evt->currency, amount, sizeof(amount));
    pb_ascii_fold(evt->sender, sender, sizeof(sender));
    pb_ascii_fold(evt->source, source, sizeof(source));
    if (!lvgl_port_lock(100)) {
        return;
    }
    lv_label_set_text(s_source, source);
    lv_label_set_text(s_amount, amount);
    lv_label_set_text(s_sender, sender[0] != '\0' ? sender : "");
    lv_label_set_text_fmt(s_meta, "%s  -  %s", time_hhmm,
                          evt->verification == PB_VERIFICATION_VERIFIED ? "Verificado" : "Detectado (no verificado)");
    lv_label_set_text_fmt(s_last, "Ultimo: %s %s", amount, time_hhmm);
    lvgl_port_unlock();
}

void pb_display_show_passkey(uint32_t passkey)
{
    if (!lvgl_port_lock(100)) {
        return;
    }
    if (passkey == 0) {
        lv_obj_add_flag(s_passkey_box, LV_OBJ_FLAG_HIDDEN);
    } else {
        lv_label_set_text_fmt(s_passkey_label, "%06lu", (unsigned long)passkey);
        lv_obj_remove_flag(s_passkey_box, LV_OBJ_FLAG_HIDDEN);
    }
    lvgl_port_unlock();
}
