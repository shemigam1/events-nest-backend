<mxfile host="app.diagrams.net" modified="2026-05-14T00:00:00.000Z" agent="events-nest" version="22.1.0" type="device">

  <!-- ══════════════════════════════════════════════════════════
       PAGE 1 — Core ERD
       Covers the transactional core: identity, events, ticketing,
       bookings, check-in. Every table has a persistent @Entity.
       Cardinality: ERone → ERmany unless labelled 1:1 or 0..1
       ══════════════════════════════════════════════════════════ -->
  <diagram id="erd-core" name="1 - Core ERD">
    <mxGraphModel dx="1600" dy="1000" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1800" pageHeight="1300" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        <mxCell id="t0" value="Events Nest — Core ERD (PostgreSQL / Flyway)" style="text;html=1;strokeColor=none;fillColor=none;align=left;fontSize=16;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="40" y="20" width="700" height="30" as="geometry"/>
        </mxCell>

        <!-- ──────────── ROW 1: identity hub ──────────── -->

        <!-- users: vendor_* columns added in V17+ -->
        <mxCell id="users" value="&lt;b&gt;users&lt;/b&gt;&lt;hr/&gt;PK id (varchar 12)&lt;br/&gt;email UK&lt;br/&gt;first_name, last_name&lt;br/&gt;password_hash, role&lt;br/&gt;enabled (bool)&lt;br/&gt;vendor_verified (bool)&lt;br/&gt;vendor_verification_status&lt;br/&gt;vendor_service_type" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="60" y="70" width="255" height="160" as="geometry"/>
        </mxCell>

        <!-- events: pending_description + has_pending_update track in-flight edits -->
        <mxCell id="events" value="&lt;b&gt;events&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK created_by → users&lt;br/&gt;code UK (12 chars), title&lt;br/&gt;venue, start_time, end_time&lt;br/&gt;check_in_start_time&lt;br/&gt;status (DRAFT/PENDING/PUBLISHED…)&lt;br/&gt;visibility (PUBLIC / PRIVATE)&lt;br/&gt;cover_image_url&lt;br/&gt;pending_description, has_pending_update&lt;br/&gt;rejection_reason" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="400" y="50" width="300" height="205" as="geometry"/>
        </mxCell>

        <!-- event_days: optional day-by-day breakdown -->
        <mxCell id="event_days" value="&lt;b&gt;event_days&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK event_id → events&lt;br/&gt;day_number, title, date&lt;br/&gt;start_time, end_time&lt;br/&gt;check_in_start_time&lt;br/&gt;UK (event_id, day_number)" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="815" y="50" width="235" height="135" as="geometry"/>
        </mxCell>

        <!-- event_configs: @OneToOne with events — NOT one-to-many -->
        <mxCell id="event_configs" value="&lt;b&gt;event_configs&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK event_id → events  (1:1 unique)&lt;br/&gt;ticketing_enabled  (default T)&lt;br/&gt;guest_list_enabled  (default F)&lt;br/&gt;programme_enabled  (default T)&lt;br/&gt;ratings_enabled  (default F)&lt;br/&gt;comments_enabled  (default T)" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="815" y="215" width="270" height="150" as="geometry"/>
        </mxCell>

        <!-- ──────────── ROW 2: event membership / edit ──────────── -->

        <!-- event_memberships: permissions is a Postgres VARCHAR ARRAY -->
        <mxCell id="event_memberships" value="&lt;b&gt;event_memberships&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK user_id → users&lt;br/&gt;FK event_id → events&lt;br/&gt;FK assigned_by → users (opt.)&lt;br/&gt;role (EventRole enum)&lt;br/&gt;status (ACTIVE…)&lt;br/&gt;permissions (VARCHAR[])&lt;br/&gt;UK (user_id, event_id, role)" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="60" y="305" width="285" height="165" as="geometry"/>
        </mxCell>

        <mxCell id="event_edit_requests" value="&lt;b&gt;event_edit_requests&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK event_id → events&lt;br/&gt;FK submitted_by → users&lt;br/&gt;proposed_changes (JSON)&lt;br/&gt;status (PENDING…)&lt;br/&gt;rejection_reason, reviewed_at" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="435" y="300" width="285" height="140" as="geometry"/>
        </mxCell>

        <!-- check_in_invites: name+email added; last_used_at for audit -->
        <mxCell id="check_in_invites" value="&lt;b&gt;check_in_invites&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK event_id → events&lt;br/&gt;FK created_by → users&lt;br/&gt;name (100 chars)&lt;br/&gt;email (optional)&lt;br/&gt;token_hash UK (255)&lt;br/&gt;status (ACTIVE / REVOKED)&lt;br/&gt;expires_at, last_used_at (opt.)" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="815" y="395" width="265" height="180" as="geometry"/>
        </mxCell>

        <!-- ──────────── ROW 3: ticketing ──────────── -->

        <!-- ticket_tiers: seating grid fields made explicit -->
        <mxCell id="ticket_tiers" value="&lt;b&gt;ticket_tiers&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK event_id → events&lt;br/&gt;FK event_day_id → event_days (opt.)&lt;br/&gt;name (100 chars), price&lt;br/&gt;row_prefix, row_count, seats_per_row&lt;br/&gt;total_capacity, available_capacity&lt;br/&gt;version  (optimistic lock)" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#ffe6cc;strokeColor=#d79b00;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="435" y="500" width="310" height="165" as="geometry"/>
        </mxCell>

        <!-- ──────────── ROW 4: bookings ──────────── -->

        <!-- bookings: payment_reference + payment_gateway_ref (provider-agnostic idempotency key) -->
        <mxCell id="bookings" value="&lt;b&gt;bookings&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK attendee_id → users&lt;br/&gt;FK event_id → events&lt;br/&gt;FK tier_id → ticket_tiers&lt;br/&gt;quantity, total_amount&lt;br/&gt;status (CONFIRMED…)&lt;br/&gt;payment_status (PAID…)&lt;br/&gt;payment_reference (100 chars)&lt;br/&gt;payment_gateway_ref UK&lt;br/&gt;paid_at, version" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="60" y="545" width="290" height="200" as="geometry"/>
        </mxCell>

        <!-- ──────────── ROW 5: tickets + check-in log ──────────── -->

        <mxCell id="tickets" value="&lt;b&gt;tickets&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK booking_id → bookings&lt;br/&gt;FK tier_id → ticket_tiers&lt;br/&gt;FK attendee_id → users (denorm.)&lt;br/&gt;seat_number&lt;br/&gt;qr_code UK, short_code UK (10)&lt;br/&gt;status (VALID…)&lt;br/&gt;checked_in_at" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="60" y="820" width="290" height="170" as="geometry"/>
        </mxCell>

        <!-- ticket_check_ins: NEW — records each physical check-in event per day -->
        <mxCell id="ticket_check_ins" value="&lt;b&gt;ticket_check_ins&lt;/b&gt;&lt;hr/&gt;PK id (uuid)&lt;br/&gt;FK ticket_id → tickets&lt;br/&gt;FK event_day_id → event_days&lt;br/&gt;checked_in_at&lt;br/&gt;checked_in_by_label (100 chars)&lt;br/&gt;UK (ticket_id, event_day_id)" style="shape=table;startSize=0;container=0;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=0;columnLines=0;align=left;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="450" y="730" width="270" height="140" as="geometry"/>
        </mxCell>

        <!-- ──────────── EDGES ──────────── -->

        <!-- users → events -->
        <mxCell id="e_ue" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;exitX=1;exitY=0.35;entryX=0;entryY=0.35;" edge="1" parent="1" source="users" target="events">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- events → event_days -->
        <mxCell id="e_ed" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="events" target="event_days">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- events → event_configs  (1:1 — fixed from previous ERmany) -->
        <mxCell id="e_ec" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERone;startArrow=ERone;startFill=0;endFill=0;" edge="1" parent="1" source="events" target="event_configs">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>
        <mxCell id="e_ec_lbl" value="1:1" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=9;" connectable="0" vertex="1" parent="e_ec">
          <mxGeometry relative="1" x="0.3" as="geometry"><mxPoint as="offset"/></mxGeometry>
        </mxCell>

        <!-- users → event_memberships -->
        <mxCell id="e_em_u" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="users" target="event_memberships">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- events → event_memberships -->
        <mxCell id="e_em_e" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="events" target="event_memberships">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- events → event_edit_requests -->
        <mxCell id="e_eer" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="events" target="event_edit_requests">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- events → ticket_tiers -->
        <mxCell id="e_tt" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="events" target="ticket_tiers">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- event_days → ticket_tiers  (optional FK) -->
        <mxCell id="e_tt_ed" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERzeroToOne;startFill=0;dashed=1;" edge="1" parent="1" source="event_days" target="ticket_tiers">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- events → check_in_invites -->
        <mxCell id="e_ci" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="events" target="check_in_invites">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- ticket_tiers → bookings  (exit left of tiers, enter right of bookings) -->
        <mxCell id="e_bk" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;exitX=0;exitY=0.5;entryX=1;entryY=0.35;" edge="1" parent="1" source="ticket_tiers" target="bookings">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- users → bookings  (route left of event_memberships to avoid crossing) -->
        <mxCell id="e_bk_u" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;exitX=0;exitY=0.85;entryX=0;entryY=0.1;" edge="1" parent="1" source="users" target="bookings">
          <mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="25" y="500"/></Array></mxGeometry>
        </mxCell>

        <!-- events → bookings  (route around event_memberships + event_edit_requests) -->
        <mxCell id="e_bk_ev" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="events" target="bookings">
          <mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="550" y="470"/><mxPoint x="205" y="470"/></Array></mxGeometry>
        </mxCell>

        <!-- bookings → tickets -->
        <mxCell id="e_tk" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="bookings" target="tickets">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- ticket_tiers → tickets  (route right to avoid ticket_check_ins) -->
        <mxCell id="e_tk_t" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="ticket_tiers" target="tickets">
          <mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="800" y="850"/><mxPoint x="350" y="850"/></Array></mxGeometry>
        </mxCell>

        <!-- tickets → ticket_check_ins  (NEW — straight right) -->
        <mxCell id="e_tci_tk" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;exitX=1;exitY=0.4;entryX=0;entryY=0.5;" edge="1" parent="1" source="tickets" target="ticket_check_ins">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- event_days → ticket_check_ins  (route down right side to avoid check_in_invites) -->
        <mxCell id="e_tci_ed" style="edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;startFill=0;" edge="1" parent="1" source="event_days" target="ticket_check_ins">
          <mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="1110" y="115"/><mxPoint x="1110" y="800"/><mxPoint x="720" y="800"/></Array></mxGeometry>
        </mxCell>

      </root>
    </mxGraphModel>
  </diagram>

  <!-- ══════════════════════════════════════════════════════════
       PAGE 2 — Extended ERD
       Reference map of all remaining tables.
       events and users appear as hub nodes (no full column list).
       Dashed arrows = optional FK or logical reference.
       ══════════════════════════════════════════════════════════ -->
  <diagram id="erd-extended" name="2 - Extended ERD">
    <mxGraphModel dx="1800" dy="1100" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="2200" pageHeight="950" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        <mxCell id="tx" value="Events Nest — Extended ERD (notifications · engagement · chat · vendors · contracts · budget)" style="text;html=1;strokeColor=none;fillColor=none;align=left;fontSize=15;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="40" y="15" width="1100" height="28" as="geometry"/>
        </mxCell>

        <!-- ── Central hubs ── -->
        <mxCell id="events_ref" value="&lt;b&gt;events&lt;/b&gt; (hub)" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="960" y="110" width="150" height="50" as="geometry"/>
        </mxCell>
        <mxCell id="users_ref" value="&lt;b&gt;users&lt;/b&gt; (hub)" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontStyle=1" vertex="1" parent="1">
          <mxGeometry x="960" y="195" width="150" height="50" as="geometry"/>
        </mxCell>

        <!-- ── Left column: notifications · email · comments · audit ── -->
        <mxCell id="notifications" value="&lt;b&gt;notifications&lt;/b&gt;&lt;br/&gt;userId (String, no FK)&lt;br/&gt;type, dedupe_key UK&lt;br/&gt;title, message, read&lt;br/&gt;Index (user_id, created_at)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="70" y="90" width="245" height="90" as="geometry"/>
        </mxCell>
        <mxCell id="email_jobs" value="&lt;b&gt;email_jobs&lt;/b&gt;&lt;br/&gt;outbox table (decoupled FKs)&lt;br/&gt;job_type, status, retry_count&lt;br/&gt;sent_at, failure_reason" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="70" y="210" width="230" height="80" as="geometry"/>
        </mxCell>
        <mxCell id="comments" value="&lt;b&gt;event_comments&lt;/b&gt;, &lt;b&gt;comment_reactions&lt;/b&gt;&lt;br/&gt;author → users; event → events&lt;br/&gt;parent_id (nullable, one-level replies)&lt;br/&gt;soft-delete via deleted_at" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="70" y="320" width="265" height="85" as="geometry"/>
        </mxCell>
        <mxCell id="pwd" value="&lt;b&gt;password_reset_tokens&lt;/b&gt;&lt;br/&gt;userId (String, no FK)&lt;br/&gt;token UK (128 chars), used&lt;br/&gt;expires_at" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="70" y="435" width="245" height="75" as="geometry"/>
        </mxCell>

        <!-- CalendarService note (NOT a DB table) -->
        <mxCell id="cal_note" value="&lt;i&gt;CalendarService&lt;/i&gt; (no table)&lt;br/&gt;Generates Google Calendar URL&lt;br/&gt;and iCal (.ics) content on-demand&lt;br/&gt;— attached to booking confirmation emails" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#ffffc0;strokeColor=#d6b656;fontSize=10;fontStyle=2;" vertex="1" parent="1">
          <mxGeometry x="70" y="535" width="265" height="80" as="geometry"/>
        </mxCell>

        <!-- ── Mid-left: guests · programme · ratings · admin_inv ── -->
        <mxCell id="guests" value="&lt;b&gt;guests&lt;/b&gt;&lt;br/&gt;FK event_id → events&lt;br/&gt;name, email, phone&lt;br/&gt;rsvp_status, rsvp_token_hash&lt;br/&gt;UK (event_id, email)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="380" y="90" width="230" height="90" as="geometry"/>
        </mxCell>
        <mxCell id="programme" value="&lt;b&gt;programme_items&lt;/b&gt;&lt;br/&gt;FK event_id → events&lt;br/&gt;FK event_day_id (optional)&lt;br/&gt;title, speaker, start/end_time&lt;br/&gt;display_order" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="380" y="210" width="230" height="85" as="geometry"/>
        </mxCell>
        <!-- ratings: 4 tables listed explicitly -->
        <mxCell id="ratings" value="&lt;b&gt;rating_forms&lt;/b&gt;  (1:1 → events)&lt;br/&gt;&lt;b&gt;rating_questions&lt;/b&gt;  → rating_forms&lt;br/&gt;&lt;b&gt;rating_responses&lt;/b&gt;  → rating_forms&lt;br/&gt;&lt;b&gt;rating_answers&lt;/b&gt;  → rating_responses&lt;br/&gt;&amp; rating_questions" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="380" y="325" width="260" height="100" as="geometry"/>
        </mxCell>
        <mxCell id="admin_inv" value="&lt;b&gt;admin_invitations&lt;/b&gt;&lt;br/&gt;email UK, token UK&lt;br/&gt;expires_at, used (bool)&lt;br/&gt;(no FK — standalone)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="380" y="455" width="220" height="75" as="geometry"/>
        </mxCell>

        <!-- ── Mid-center: chat cluster · budget · event managers ── -->
        <!-- chat: all 3 tables listed -->
        <mxCell id="chat" value="&lt;b&gt;conversations&lt;/b&gt;  (type, optional event FK)&lt;br/&gt;&lt;b&gt;messages&lt;/b&gt;  → conversations, users&lt;br/&gt;STOMP /ws delivers in real-time" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="660" y="80" width="280" height="85" as="geometry"/>
        </mxCell>
        <!-- conversation_participants: NEW -->
        <mxCell id="conv_participants" value="&lt;b&gt;conversation_participants&lt;/b&gt;&lt;br/&gt;FK conversation_id → conversations&lt;br/&gt;FK user_id → users&lt;br/&gt;joined_at, last_read_at&lt;br/&gt;UK (conversation_id, user_id)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="660" y="195" width="280" height="95" as="geometry"/>
        </mxCell>
        <mxCell id="budget" value="&lt;b&gt;event_budgets&lt;/b&gt;  (1:1 → events)&lt;br/&gt;&lt;b&gt;budget_line_items&lt;/b&gt;  → event_budgets&lt;br/&gt;category (enum), planned/actual_amount&lt;br/&gt;status (PLANNED / PAID)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#ffe6cc;strokeColor=#d79b00;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="660" y="320" width="270" height="90" as="geometry"/>
        </mxCell>
        <!-- event managers cluster: 3 dedicated tables (NOT event_memberships) -->
        <mxCell id="em_app" value="&lt;b&gt;event_manager_applications&lt;/b&gt;&lt;br/&gt;FK applicant_id → users  (1:1)&lt;br/&gt;FK reviewed_by → users (opt.)&lt;br/&gt;display_name, bio, specialties&lt;br/&gt;years_of_experience, past_events_count&lt;br/&gt;location, portfolio_url&lt;br/&gt;status (PENDING / APPROVED / REJECTED)&lt;br/&gt;reviewed_at, review_notes" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="660" y="440" width="280" height="155" as="geometry"/>
        </mxCell>
        <mxCell id="em_inv" value="&lt;b&gt;event_manager_invitations&lt;/b&gt;&lt;br/&gt;FK event_id → events&lt;br/&gt;FK manager_user_id → users&lt;br/&gt;FK invited_by → users&lt;br/&gt;permissions (text[])&lt;br/&gt;status (PENDING / ACCEPTED / DECLINED)&lt;br/&gt;UK (event_id, manager_user_id)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="660" y="615" width="280" height="120" as="geometry"/>
        </mxCell>
        <mxCell id="em_prof" value="&lt;b&gt;event_manager_profiles&lt;/b&gt;&lt;br/&gt;FK user_id → users  (1:1)&lt;br/&gt;display_name, bio, specialties&lt;br/&gt;years_of_experience, past_events_count&lt;br/&gt;location, portfolio_url&lt;br/&gt;suspended (bool)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="660" y="755" width="280" height="115" as="geometry"/>
        </mxCell>

        <!-- Kafka topics legend (in hub area) -->
        <mxCell id="kafka_legend" value="&lt;b&gt;Kafka topics (6)&lt;/b&gt;&lt;br/&gt;ticket.checked-in  ·  booking.confirmed&lt;br/&gt;event.approved  ·  event.rejected&lt;br/&gt;contract.signed  ·  audit.events" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="960" y="310" width="250" height="85" as="geometry"/>
        </mxCell>

        <!-- ── Right: vendor · contracts · escrow ── -->
        <mxCell id="vendor" value="&lt;b&gt;vendor_applications&lt;/b&gt;&lt;br/&gt;FK event_id → events&lt;br/&gt;FK applicant_id → users&lt;br/&gt;service_type, proposed_amount&lt;br/&gt;status, UK (event_id, applicant_id, svc)&lt;br/&gt;&lt;b&gt;vendor_inquiries&lt;/b&gt;  → events, users" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1200" y="80" width="295" height="120" as="geometry"/>
        </mxCell>
        <!-- vendor_ratings: NEW — OneToOne with vendor_applications -->
        <mxCell id="vendor_ratings" value="&lt;b&gt;vendor_ratings&lt;/b&gt;  (1:1 → vendor_applications)&lt;br/&gt;FK rated_by → users&lt;br/&gt;score (1–5), comment&lt;br/&gt;UK (vendor_application_id)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1530" y="80" width="295" height="90" as="geometry"/>
        </mxCell>
        <mxCell id="contracts" value="&lt;b&gt;vendor_contracts&lt;/b&gt;&lt;br/&gt;FK event_id → events&lt;br/&gt;FK organizer_id, vendor_id → users&lt;br/&gt;FK vendor_application_id (opt.)&lt;br/&gt;FK conversation_id (opt.)&lt;br/&gt;title, terms, amount&lt;br/&gt;status (DRAFT→SIGNED→FUNDED→ACTIVE…)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1200" y="225" width="295" height="135" as="geometry"/>
        </mxCell>
        <mxCell id="escrow" value="&lt;b&gt;escrow_accounts&lt;/b&gt;  (1:1 → vendor_contracts)&lt;br/&gt;total_amount, released_amount&lt;br/&gt;status (PENDING / FUNDED / CLOSED)" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1200" y="385" width="295" height="75" as="geometry"/>
        </mxCell>
        <!-- escrow_milestones: NEW — child of escrow_accounts -->
        <mxCell id="escrow_milestones" value="&lt;b&gt;escrow_milestones&lt;/b&gt;  → escrow_accounts&lt;br/&gt;title, amount, display_order&lt;br/&gt;status (PENDING / APPROVED / RELEASED)&lt;br/&gt;approved_at, released_at" style="shape=table;startSize=0;container=0;collapsible=0;align=left;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1530" y="225" width="295" height="90" as="geometry"/>
        </mxCell>

        <!-- ── Legend ── -->
        <mxCell id="leg" value="Dashed = optional FK or logical reference. 37 @Entity classes total (11 Core + 26 Extended). Kafka audit trail uses topic audit.events — no persistent audit_logs table." style="text;html=1;strokeColor=none;fillColor=none;align=left;fontSize=10;fontStyle=2" vertex="1" parent="1">
          <mxGeometry x="70" y="890" width="1000" height="35" as="geometry"/>
        </mxCell>

        <!-- ── Edges (hub-and-spoke keeps crossing count low) ── -->

        <!-- Left column → hubs -->
        <mxCell id="r1"  style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="notifications"   target="users_ref">   <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r2"  style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="guests"          target="events_ref">   <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r3"  style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="programme"       target="events_ref">   <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r4"  style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="ratings"         target="events_ref">   <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r5"  style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="comments"        target="events_ref">   <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r6"  style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="comments"        target="users_ref">    <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r7"  style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="pwd"             target="users_ref">    <mxGeometry relative="1" as="geometry"/></mxCell>

        <!-- Mid columns → hubs -->
        <mxCell id="r8"  style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="chat"            target="users_ref">    <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r9"  style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="conv_participants" target="users_ref">  <mxGeometry relative="1" as="geometry"/></mxCell>
        <!-- conversation_participants → chat (conversations) -->
        <mxCell id="r9b" style="endArrow=open;dashed=1;html=1;exitX=0.5;exitY=0;entryX=0.5;entryY=1;" edge="1" parent="1" source="conv_participants" target="chat">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>
        <mxCell id="r10" style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="budget"          target="events_ref">   <mxGeometry relative="1" as="geometry"/></mxCell>
        <!-- event manager cluster → hubs -->
        <mxCell id="r11a" style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="em_app"  target="users_ref">   <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r11b" style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="em_inv"  target="events_ref"> <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r11c" style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="em_inv"  target="users_ref">  <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r11d" style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="em_prof" target="users_ref">  <mxGeometry relative="1" as="geometry"/></mxCell>

        <!-- Right column → hubs -->
        <mxCell id="r12" style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="vendor"          target="events_ref">   <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r13" style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="contracts"       target="events_ref">   <mxGeometry relative="1" as="geometry"/></mxCell>
        <mxCell id="r14" style="endArrow=open;dashed=1;html=1;" edge="1" parent="1" source="contracts"       target="users_ref">    <mxGeometry relative="1" as="geometry"/></mxCell>

        <!-- escrow → escrow_milestones (1→many, rightward) -->
        <mxCell id="r15" style="endArrow=open;dashed=1;html=1;exitX=1;exitY=0.5;entryX=0;entryY=0.5;" edge="1" parent="1" source="escrow" target="escrow_milestones">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>
        <!-- escrow_accounts 1:1 with contracts -->
        <mxCell id="r16" style="endArrow=open;dashed=1;html=1;exitX=0.5;exitY=0;entryX=0.5;entryY=1;" edge="1" parent="1" source="escrow" target="contracts">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

        <!-- vendor_ratings 1:1 with vendor_applications (rightward from vendor box) -->
        <mxCell id="r17" style="endArrow=open;dashed=1;html=1;exitX=1;exitY=0.4;entryX=0;entryY=0.5;" edge="1" parent="1" source="vendor" target="vendor_ratings">
          <mxGeometry relative="1" as="geometry"/>
        </mxCell>

      </root>
    </mxGraphModel>
  </diagram>

</mxfile>
