#include "diag.h"

#include <string.h>

#include "esp_log.h"

static char s_error[96];

void pb_diag_set_error(const char *msg)
{
    strncpy(s_error, msg, sizeof(s_error) - 1);
    s_error[sizeof(s_error) - 1] = '\0';
    ESP_LOGE("diag", "%s", s_error);
}

void pb_diag_clear_error(void) { s_error[0] = '\0'; }

const char *pb_diag_error(void) { return s_error[0] != '\0' ? s_error : NULL; }
