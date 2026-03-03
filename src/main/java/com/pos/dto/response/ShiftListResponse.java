package com.pos.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ShiftListResponse {
    private long openCount;
    private List<ShiftResponse> shifts;
}
