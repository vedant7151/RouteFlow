package com.routeflow.service;

import com.routeflow.domain.Route;
import com.routeflow.domain.RouteStop;
import com.routeflow.domain.enums.OrderStatus;
import com.routeflow.domain.enums.RouteStatus;
import com.routeflow.domain.enums.StopStatus;
import com.routeflow.dto.stop.StopStatusUpdateRequest;
import com.routeflow.exception.BadRequestException;
import com.routeflow.repository.RouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StopServiceTest {

    @Mock RouteRepository routeRepository;
    @Mock OrderService orderService;

    private StopService service;
    private Route route;
    private RouteStop first;
    private RouteStop second;

    @BeforeEach
    void setUp() {
        first = RouteStop.builder().id("s1").orderId("o1").sequence(1).status(StopStatus.PENDING).build();
        second = RouteStop.builder().id("s2").orderId("o2").sequence(2).status(StopStatus.PENDING).build();
        route = Route.builder().id("r1").status(RouteStatus.DISPATCHED).stops(new ArrayList<>(List.of(first, second))).build();

        when(routeRepository.findByStopsId(any())).thenReturn(Optional.of(route));
        when(routeRepository.findById("r1")).thenReturn(Optional.of(route));
        when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new StopService(routeRepository, orderService);
    }

    private StopStatusUpdateRequest to(StopStatus status) {
        return new StopStatusUpdateRequest(status, null, null, null);
    }

    @Test
    void firstUpdateOnADispatchedRouteMovesItToInProgressAndSyncsTheOrder() {
        service.updateStatus("s1", to(StopStatus.EN_ROUTE));

        assertEquals(RouteStatus.IN_PROGRESS, route.getStatus());
        verify(orderService).updateStatus("o1", OrderStatus.EN_ROUTE, null);
    }

    @Test
    void routeCompletesOnceEveryStopIsDeliveredOrFailed() {
        service.updateStatus("s1", to(StopStatus.DELIVERED));
        assertEquals(RouteStatus.IN_PROGRESS, route.getStatus());

        service.updateStatus("s2", new StopStatusUpdateRequest(StopStatus.FAILED, "Customer absent", null, null));

        assertEquals(RouteStatus.COMPLETED, route.getStatus());
        verify(orderService).updateStatus("o2", OrderStatus.FAILED, "Customer absent");
    }

    @Test
    void arrivalTimeIsKeptWhenALaterDeliveredUpdateArrives() {
        service.updateStatus("s1", to(StopStatus.ARRIVED));
        var arrival = first.getActualArrival();
        assertNotNull(arrival);

        service.updateStatus("s1", to(StopStatus.DELIVERED));

        assertEquals(arrival, first.getActualArrival());
    }

    @Test
    void aFinishedStopCannotChangeToADifferentStatus() {
        service.updateStatus("s1", to(StopStatus.DELIVERED));

        assertThrows(BadRequestException.class, () -> service.updateStatus("s1", to(StopStatus.FAILED)));
    }

    @Test
    void repeatingTheSameTerminalStatusIsAHarmlessNoOp() {
        service.updateStatus("s1", to(StopStatus.DELIVERED));

        service.updateStatus("s1", to(StopStatus.DELIVERED));

        verify(orderService).updateStatus(eq("o1"), eq(OrderStatus.DELIVERED), any());
    }

    @Test
    void stopsOfARouteThatWasNeverDispatchedCannotBeUpdated() {
        route.setStatus(RouteStatus.PLANNED);

        assertThrows(BadRequestException.class, () -> service.updateStatus("s1", to(StopStatus.ARRIVED)));
        verify(routeRepository, never()).save(any());
    }

    @Test
    void startRouteMovesDispatchedToInProgressButRejectsPlanned() {
        assertEquals(RouteStatus.IN_PROGRESS, service.startRoute("r1").getStatus());

        route.setStatus(RouteStatus.PLANNED);
        assertThrows(BadRequestException.class, () -> service.startRoute("r1"));
    }
}
