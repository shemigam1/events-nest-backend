<mxfile host="app.diagrams.net" modified="2026-05-14T00:00:00.000Z" agent="events-nest" version="22.1.0" type="device">
  <diagram id="uml-core-domain" name="1 - Core domain classes">
    <mxGraphModel dx="1600" dy="1000" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1800" pageHeight="950" math="0" shadow="0">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
        <mxCell id="t1" parent="1" style="text;html=1;strokeColor=none;fillColor=none;align=left;fontSize=14;fontStyle=1" value="Core domain (JPA) — simplified UML class view" vertex="1">
          <mxGeometry height="30" width="700" x="40" y="20" as="geometry" />
        </mxCell>

        <!-- ── Row 1: User · Events · EventConfig · EventDay ── -->

        <mxCell id="User" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" value="&lt;b&gt;User&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;id : String&lt;br/&gt;email : String&lt;br/&gt;role : Role&lt;br/&gt;vendorVerified : boolean&lt;br/&gt;vendorVerificationStatus&lt;br/&gt;vendorServiceType : String&lt;br/&gt;…&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;auth.model&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="175" width="210" x="80" y="70" as="geometry" />
        </mxCell>

        <mxCell id="Events" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" value="&lt;b&gt;Events&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;id : UUID&lt;br/&gt;code : String&lt;br/&gt;title, venue, times&lt;br/&gt;status : EventStatus&lt;br/&gt;visibility : EventVisibility&lt;br/&gt;coverImageUrl : String&lt;br/&gt;pendingDescription : String&lt;br/&gt;hasPendingUpdate : boolean&lt;br/&gt;…&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;events.models&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="220" width="270" x="550" y="60" as="geometry" />
        </mxCell>

        <mxCell id="EventConfig" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" value="&lt;b&gt;EventConfig&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;id : UUID&lt;br/&gt;ticketingEnabled : boolean&lt;br/&gt;guestListEnabled : boolean&lt;br/&gt;programmeEnabled : boolean&lt;br/&gt;ratingsEnabled : boolean&lt;br/&gt;commentsEnabled : boolean&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;events.models  (1:1)&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="165" width="225" x="890" y="60" as="geometry" />
        </mxCell>

        <mxCell id="EventDay" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;" value="&lt;b&gt;EventDay&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;id : UUID&lt;br/&gt;dayNumber, label, date…&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;events.models&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="120" width="210" x="1250" y="60" as="geometry" />
        </mxCell>

        <!-- ── Row 2: EventMembership · CheckInInvite · TicketCheckIn ── -->

        <mxCell id="EventMembership" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;" value="&lt;b&gt;EventMembership&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;role : EventRole&lt;br/&gt;status : MembershipStatus&lt;br/&gt;permissions : VARCHAR[]&lt;br/&gt;…&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;events.models&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="135" width="225" x="250" y="355" as="geometry" />
        </mxCell>

        <mxCell id="CheckInInvite" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;" value="&lt;b&gt;CheckInInvite&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;id : UUID&lt;br/&gt;name : String&lt;br/&gt;email : String&lt;br/&gt;tokenHash : String&lt;br/&gt;status : InviteStatus&lt;br/&gt;expiresAt, lastUsedAt&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;checkin.model&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="165" width="225" x="890" y="305" as="geometry" />
        </mxCell>

        <mxCell id="TicketCheckIn" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;" value="&lt;b&gt;TicketCheckIn&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;id : UUID&lt;br/&gt;checkedInAt : Instant&lt;br/&gt;checkedInByLabel : String&lt;br/&gt;UK(ticket_id, event_day_id)&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;checkin.model&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="130" width="225" x="1390" y="380" as="geometry" />
        </mxCell>

        <!-- ── Row 3: Booking · TicketTier · Ticket ── -->

        <mxCell id="Booking" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" value="&lt;b&gt;Booking&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;id : UUID&lt;br/&gt;quantity : int&lt;br/&gt;totalAmount : Decimal&lt;br/&gt;status, paymentStatus&lt;br/&gt;paymentGatewayRef&lt;br/&gt;paymentReference : String&lt;br/&gt;version : int&lt;br/&gt;…&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;bookings.models&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="200" width="265" x="60" y="680" as="geometry" />
        </mxCell>

        <mxCell id="TicketTier" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;" value="&lt;b&gt;TicketTier&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;id : UUID&lt;br/&gt;name, price&lt;br/&gt;rowPrefix : String&lt;br/&gt;rowCount : int&lt;br/&gt;seatsPerRow : int&lt;br/&gt;totalCapacity : int&lt;br/&gt;availableCapacity : int&lt;br/&gt;version : int&lt;br/&gt;…&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;tiers.models&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="210" width="255" x="520" y="530" as="geometry" />
        </mxCell>

        <mxCell id="Ticket" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" value="&lt;b&gt;Ticket&lt;/b&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;id : UUID&lt;br/&gt;seatNumber, qrCode, shortCode&lt;br/&gt;status : TicketStatus&lt;br/&gt;checkedInAt : Instant&lt;br/&gt;…&lt;/div&gt;&lt;hr/&gt;&lt;div align=&quot;left&quot;&gt;&lt;i&gt;tickets.models&lt;/i&gt;&lt;/div&gt;" vertex="1">
          <mxGeometry height="155" width="250" x="1100" y="680" as="geometry" />
        </mxCell>

        <!-- ══ Edges ══ -->

        <!-- User –createdBy–> Events (1:*) -->
        <mxCell id="e_ue" edge="1" parent="1" source="User" style="endArrow=block;endFill=0;html=1;exitX=1;exitY=0.3;entryX=0;entryY=0.3;fontSize=10;" target="Events" value="1">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_ue_l" connectable="0" parent="e_ue" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=9;" value="createdBy" vertex="1">
          <mxGeometry relative="1" x="-0.2" as="geometry"><mxPoint as="offset" /></mxGeometry>
        </mxCell>

        <!-- User → EventMembership -->
        <mxCell id="e_um_u" edge="1" parent="1" source="User" style="endArrow=none;html=1;exitX=0.5;exitY=1;entryX=0.2;entryY=0;" target="EventMembership">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- EventMembership → Events -->
        <mxCell id="e_um_e" edge="1" parent="1" source="EventMembership" style="endArrow=none;html=1;exitX=0.8;exitY=0;entryX=0.3;entryY=1;" target="Events">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- Events –1:1–> EventConfig -->
        <mxCell id="e_ec" edge="1" parent="1" source="Events" style="endArrow=block;endFill=0;html=1;exitX=1;exitY=0.25;entryX=0;entryY=0.25;fontSize=10;" target="EventConfig" value="1:1">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- Events –1:*–> EventDay (route above EventConfig) -->
        <mxCell id="e_ed" edge="1" parent="1" source="Events" style="endArrow=block;endFill=0;html=1;exitX=0.85;exitY=0;entryX=0.5;entryY=0;fontSize=10;" target="EventDay" value="1">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="780" y="30" />
              <mxPoint x="1355" y="30" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="e_ed_l" connectable="0" parent="e_ed" style="edgeLabel;html=1;align=right;verticalAlign=middle;fontSize=10;" value="*" vertex="1">
          <mxGeometry relative="1" x="0.85" as="geometry"><mxPoint x="10" as="offset" /></mxGeometry>
        </mxCell>

        <!-- Events –1:*–> CheckInInvite -->
        <mxCell id="e_eci" edge="1" parent="1" source="Events" style="endArrow=block;endFill=0;html=1;exitX=1;exitY=0.85;entryX=0;entryY=0.2;fontSize=10;" target="CheckInInvite" value="1">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_eci_l" connectable="0" parent="e_eci" style="edgeLabel;html=1;align=right;verticalAlign=middle;fontSize=10;" value="*" vertex="1">
          <mxGeometry relative="1" x="0.85" as="geometry"><mxPoint x="10" as="offset" /></mxGeometry>
        </mxCell>

        <!-- Events –1:*–> TicketTier -->
        <mxCell id="e_et" edge="1" parent="1" source="Events" style="endArrow=block;endFill=0;html=1;exitX=0.25;exitY=1;entryX=0.3;entryY=0;fontSize=10;" target="TicketTier" value="1">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_et_l" connectable="0" parent="e_et" style="edgeLabel;html=1;align=right;verticalAlign=middle;fontSize=10;" value="*" vertex="1">
          <mxGeometry relative="1" x="0.85" as="geometry"><mxPoint x="10" as="offset" /></mxGeometry>
        </mxCell>

        <!-- TicketTier –0..1–> EventDay (route right side) -->
        <mxCell id="e_et2" edge="1" parent="1" source="TicketTier" style="endArrow=open;dashed=1;html=1;exitX=1;exitY=0.15;entryX=0;entryY=0.8;fontSize=9;" target="EventDay" value="0..1 eventDay">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="1200" y="562" />
              <mxPoint x="1200" y="156" />
            </Array>
          </mxGeometry>
        </mxCell>

        <!-- EventDay –1:*–> TicketCheckIn -->
        <mxCell id="e_tci_ed" edge="1" parent="1" source="EventDay" style="endArrow=block;endFill=0;html=1;exitX=1;exitY=1;entryX=0.3;entryY=0;fontSize=10;" target="TicketCheckIn" value="1">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="1502" y="180" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="e_tci_ed_l" connectable="0" parent="e_tci_ed" style="edgeLabel;html=1;align=right;verticalAlign=middle;fontSize=10;" value="*" vertex="1">
          <mxGeometry relative="1" x="0.85" as="geometry"><mxPoint x="10" as="offset" /></mxGeometry>
        </mxCell>

        <!-- Ticket –1:*–> TicketCheckIn (U-route via bottom) -->
        <mxCell id="e_tci_t" edge="1" parent="1" source="Ticket" style="endArrow=block;endFill=0;html=1;exitX=1;exitY=0.3;entryX=0.7;entryY=1;fontSize=10;" target="TicketCheckIn" value="1">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="1502" y="726" />
              <mxPoint x="1502" y="510" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="e_tci_t_l" connectable="0" parent="e_tci_t" style="edgeLabel;html=1;align=right;verticalAlign=middle;fontSize=10;" value="*" vertex="1">
          <mxGeometry relative="1" x="0.85" as="geometry"><mxPoint x="10" as="offset" /></mxGeometry>
        </mxCell>

        <!-- Booking –attendee *–> User (left side route) -->
        <mxCell id="e_bk_a" edge="1" parent="1" style="endArrow=none;html=1;exitX=0.1;exitY=0;entryX=0.1;entryY=1;fontSize=9;" source="Booking" target="User">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="87" y="620" />
              <mxPoint x="87" y="275" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="e_bk_a_l" connectable="0" parent="e_bk_a" style="edgeLabel;html=1;align=left;fontSize=9;" value="attendee *" vertex="1">
          <mxGeometry relative="1" x="-0.25" y="0" as="geometry"><mxPoint as="offset" /></mxGeometry>
        </mxCell>

        <!-- Booking → Events -->
        <mxCell id="e_bk_e" edge="1" parent="1" source="Booking" style="endArrow=none;html=1;exitX=0.55;exitY=0;entryX=0.35;entryY=1;" target="Events">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="216" y="620" />
              <mxPoint x="645" y="305" />
            </Array>
          </mxGeometry>
        </mxCell>

        <!-- Booking → TicketTier -->
        <mxCell id="e_bk_t" edge="1" parent="1" source="Booking" style="endArrow=none;html=1;exitX=1;exitY=0.5;entryX=0;entryY=0.7;" target="TicketTier">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- Ticket –1–> Booking -->
        <mxCell id="e_tk_b" edge="1" parent="1" source="Ticket" style="endArrow=block;endFill=0;html=1;exitX=0;exitY=0.4;entryX=1;entryY=0.5;fontSize=10;" target="Booking" value="1">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- Ticket → TicketTier -->
        <mxCell id="e_tk_t" edge="1" parent="1" source="Ticket" style="endArrow=none;html=1;exitX=0.4;exitY=0;entryX=1;entryY=0.7;" target="TicketTier">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="1200" y="640" />
            </Array>
          </mxGeometry>
        </mxCell>

      </root>
    </mxGraphModel>
  </diagram>
  <diagram id="uml-components" name="2 - Logical components">
    <mxGraphModel dx="1600" dy="1000" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1800" pageHeight="1100" math="0" shadow="0">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
        <mxCell id="t2" value="Logical view — Spring components (simplified)" style="text;html=1;strokeColor=none;fillColor=none;align=left;fontSize=14;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="40" y="20" width="560" height="30" as="geometry" />
        </mxCell>

        <!-- ── Column A: HTTP / WS clients ── -->
        <mxCell id="c_client" value="«HTTP / WS client»&#xa;Browser / mobile app" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#f5f5f5;" vertex="1" parent="1">
          <mxGeometry x="60" y="130" width="40" height="80" as="geometry" />
        </mxCell>

        <!-- ── Column B: frontend-facing components ── -->
        <mxCell id="c_ctrl" value="&lt;b&gt;REST Controllers&lt;/b&gt;&#xa;29 controllers: Auth, Events, Bookings,&#xa;Chat, Vendors, Contracts, …" style="shape=component;align=left;spacingLeft=10;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="220" y="80" width="230" height="80" as="geometry" />
        </mxCell>

        <mxCell id="c_ws" value="&lt;b&gt;WebSocket / STOMP&lt;/b&gt;&#xa;/ws · SockJS fallback&#xa;JwtChannelInterceptor · /queue/notify" style="shape=component;align=left;spacingLeft=10;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="220" y="195" width="230" height="80" as="geometry" />
        </mxCell>

        <mxCell id="c_sec" value="&lt;b&gt;Security&lt;/b&gt;&#xa;JWT filter · RateLimitFilter (Bucket4j)&#xa;@PreAuthorize · method security" style="shape=component;align=left;spacingLeft=10;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="220" y="310" width="230" height="80" as="geometry" />
        </mxCell>

        <!-- ── Column C: domain layer ── -->
        <mxCell id="c_svc" value="&lt;b&gt;Domain services&lt;/b&gt;&#xa;EventService, BookingService,&#xa;ChatService, EscrowService, …" style="shape=component;align=left;spacingLeft=10;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="550" y="80" width="240" height="80" as="geometry" />
        </mxCell>

        <mxCell id="c_repo" value="&lt;b&gt;Repositories (JPA)&lt;/b&gt;&#xa;Spring Data — 37 @Entity classes" style="shape=component;align=left;spacingLeft=10;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="550" y="200" width="240" height="70" as="geometry" />
        </mxCell>

        <mxCell id="c_db" value="&lt;b&gt;PostgreSQL&lt;/b&gt;&#xa;Flyway migrations" style="shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;size=12;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="585" y="350" width="165" height="100" as="geometry" />
        </mxCell>

        <!-- ── Column D: async / infra ── -->
        <mxCell id="c_kafka" value="&lt;b&gt;Kafka producers / consumers&lt;/b&gt;&#xa;Topics: ticket.checked-in · booking.confirmed&#xa;         event.approved · event.rejected&#xa;         contract.signed · audit.events&#xa;Groups: events-nest-notifications&#xa;           audit-consumer-group" style="shape=component;align=left;spacingLeft=10;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="900" y="60" width="275" height="130" as="geometry" />
        </mxCell>

        <mxCell id="c_sse" value="&lt;b&gt;SSE (SseEmitter)&lt;/b&gt;&#xa;Live booking / event / check-in updates" style="shape=component;align=left;spacingLeft=10;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="900" y="210" width="275" height="70" as="geometry" />
        </mxCell>

        <mxCell id="c_email" value="&lt;b&gt;Email outbox&lt;/b&gt;&#xa;EmailJob table → scheduler poller&#xa;→ Resend (transactional email)" style="shape=component;align=left;spacingLeft=10;fillColor=#ffe6cc;strokeColor=#d79b00;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="900" y="300" width="275" height="80" as="geometry" />
        </mxCell>

        <mxCell id="c_redis" value="&lt;b&gt;Redis&lt;/b&gt;&#xa;Spring Cache (JSON) · Bucket4j rate limits" style="shape=component;align=left;spacingLeft=10;fillColor=#f5f5f5;strokeColor=#666666;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="900" y="400" width="275" height="70" as="geometry" />
        </mxCell>

        <mxCell id="c_calendar" value="&lt;b&gt;CalendarService&lt;/b&gt;&#xa;Google Calendar URL + iCal on-demand&#xa;(no DB write · no external API call)" style="shape=component;align=left;spacingLeft=10;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="900" y="490" width="275" height="80" as="geometry" />
        </mxCell>

        <mxCell id="c_storage" value="&lt;b&gt;File storage&lt;/b&gt;&#xa;S3-compatible (presigned uploads)&#xa;LocalFileStorageService — dev / CI fallback" style="shape=component;align=left;spacingLeft=10;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="900" y="590" width="275" height="80" as="geometry" />
        </mxCell>

        <!-- ══ Edges ══ -->

        <!-- client → ctrl & ws -->
        <mxCell id="e1" style="endArrow=block;endFill=0;html=1;" edge="1" parent="1" source="c_client" target="c_ctrl">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e1b" style="endArrow=block;endFill=0;html=1;" edge="1" parent="1" source="c_client" target="c_ws">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- ctrl / ws both reference sec (JWT gate) -->
        <mxCell id="e2" style="endArrow=open;dashed=1;html=1;exitX=0.5;exitY=1;entryX=0.5;entryY=0;" edge="1" parent="1" source="c_ctrl" target="c_sec">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e2b" style="endArrow=open;dashed=1;html=1;exitX=0.5;exitY=1;entryX=0.5;entryY=0;" edge="1" parent="1" source="c_ws" target="c_sec">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- ctrl / ws → svc -->
        <mxCell id="e3" style="endArrow=block;endFill=0;html=1;exitX=1;exitY=0.5;entryX=0;entryY=0.35;" edge="1" parent="1" source="c_ctrl" target="c_svc">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e3b" style="endArrow=block;endFill=0;html=1;exitX=1;exitY=0.5;entryX=0;entryY=0.65;" edge="1" parent="1" source="c_ws" target="c_svc">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- svc → repo → db -->
        <mxCell id="e4" style="endArrow=block;endFill=0;html=1;" edge="1" parent="1" source="c_svc" target="c_repo">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e5" style="endArrow=block;endFill=0;html=1;" edge="1" parent="1" source="c_repo" target="c_db">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- svc –publishes–> kafka -->
        <mxCell id="e6" style="endArrow=open;dashed=1;html=1;exitX=1;exitY=0.35;entryX=0;entryY=0.5;" edge="1" parent="1" source="c_svc" target="c_kafka">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- svc –dispatches–> sse -->
        <mxCell id="e7" style="endArrow=open;dashed=1;html=1;exitX=1;exitY=0.5;entryX=0;entryY=0.5;" edge="1" parent="1" source="c_svc" target="c_sse">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- kafka consumer –triggers–> email outbox -->
        <mxCell id="e8" style="endArrow=open;dashed=1;html=1;exitX=0.5;exitY=1;entryX=0.5;entryY=0;" edge="1" parent="1" source="c_kafka" target="c_email">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- svc –caches–> redis -->
        <mxCell id="e10" style="endArrow=open;dashed=1;html=1;exitX=1;exitY=0.65;entryX=0;entryY=0.5;" edge="1" parent="1" source="c_svc" target="c_redis">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- svc –calls–> calendar -->
        <mxCell id="e11" style="endArrow=open;dashed=1;html=1;exitX=1;exitY=0.8;entryX=0;entryY=0.5;" edge="1" parent="1" source="c_svc" target="c_calendar">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>

        <!-- svc –uploads–> storage -->
        <mxCell id="e12" style="endArrow=open;dashed=1;html=1;exitX=1;exitY=1;entryX=0;entryY=0.5;" edge="1" parent="1" source="c_svc" target="c_storage">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="820" y="670" />
            </Array>
          </mxGeometry>
        </mxCell>

      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
