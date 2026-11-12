#pragma once
/* Último error relevante, para mostrarlo en pantalla e informarlo al celular. */
void pb_diag_set_error(const char *msg);
void pb_diag_clear_error(void);
const char *pb_diag_error(void); /* NULL si no hay error */
