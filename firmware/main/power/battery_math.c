#include "battery_math.h"

int pb_battery_percent(int mv)
{
    static const int table[][2] = {
        {4200, 100}, {4100, 90}, {4000, 80}, {3900, 65}, {3800, 50},
        {3700, 35}, {3600, 20}, {3500, 10}, {3400, 5}, {3300, 0},
    };
    const int n = sizeof(table) / sizeof(table[0]);
    if (mv >= table[0][0]) {
        return 100;
    }
    if (mv <= table[n - 1][0]) {
        return 0;
    }
    for (int i = 1; i < n; i++) {
        if (mv >= table[i][0]) {
            int v0 = table[i][0], p0 = table[i][1], v1 = table[i - 1][0], p1 = table[i - 1][1];
            return p0 + (mv - v0) * (p1 - p0) / (v1 - v0);
        }
    }
    return 0;
}
