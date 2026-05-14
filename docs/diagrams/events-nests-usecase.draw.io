<mxfile host="app.diagrams.net" agent="cursor">
  <diagram id="usecases-events-nest" name="Events Nest Server - Use Cases">
    <mxGraphModel dx="2943" dy="1606" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1800" pageHeight="1600" math="0" shadow="0">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
        <mxCell id="title" parent="1" style="text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;fontSize=18;fontStyle=1" value="Events Nest Server — Use Cases (aligned to REST controllers)" vertex="1">
          <mxGeometry height="40" width="1720" x="40" y="20" as="geometry" />
        </mxCell>
        <mxCell id="legend" parent="1" style="text;html=1;strokeColor=#d6b656;fillColor=#fff2cc;align=left;verticalAlign=middle;fontSize=11;fontStyle=2;spacingLeft=6;" value="Edges show primary associations only.&#xa;All authenticated actors share the Authentication package." vertex="1">
          <mxGeometry height="50" width="330" x="1420" y="510" as="geometry" />
        </mxCell>
        <mxCell id="act_attendee" parent="1" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#dae8fc;strokeColor=#6c8ebf;" value="Attendee" vertex="1">
          <mxGeometry height="80" width="40" x="40" y="90" as="geometry" />
        </mxCell>
        <mxCell id="act_guest" parent="1" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#d5e8d4;strokeColor=#82b366;" value="Guest (RSVP)" vertex="1">
          <mxGeometry height="80" width="40" x="40" y="250" as="geometry" />
        </mxCell>
        <mxCell id="act_org" parent="1" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#d5e8d4;strokeColor=#82b366;" value="Organizer" vertex="1">
          <mxGeometry height="80" width="40" x="40" y="420" as="geometry" />
        </mxCell>
        <mxCell id="act_mgr" parent="1" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#fff2cc;strokeColor=#d6b656;" value="Manager" vertex="1">
          <mxGeometry height="80" width="40" x="40" y="580" as="geometry" />
        </mxCell>
        <mxCell id="act_staff" parent="1" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#f8cecc;strokeColor=#b85450;" value="Check-in Staff" vertex="1">
          <mxGeometry height="80" width="40" x="40" y="730" as="geometry" />
        </mxCell>
        <mxCell id="act_vendor" parent="1" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#e1d5e7;strokeColor=#9673a6;" value="Vendor" vertex="1">
          <mxGeometry height="80" width="40" x="40" y="1200" as="geometry" />
        </mxCell>
        <mxCell id="act_admin" parent="1" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#f8cecc;strokeColor=#b85450;" value="Admin" vertex="1">
          <mxGeometry height="80" width="40" x="1627" y="1200" as="geometry" />
        </mxCell>
        <mxCell id="act_ext" parent="1" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#f5f5f5;strokeColor=#666666;" value="Monnify (external)" vertex="1">
          <mxGeometry height="80" width="40" x="240" y="975" as="geometry" />
        </mxCell>
        <mxCell id="pkg_auth" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#dae8fc;strokeColor=#6c8ebf;" value="Authentication" vertex="1">
          <mxGeometry height="180" width="250" x="160" y="60" as="geometry" />
        </mxCell>
        <mxCell id="uc_register" parent="pkg_auth" style="ellipse;whiteSpace=wrap;html=1;" value="Register / login / refresh token" vertex="1">
          <mxGeometry height="60" width="210" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_pwreset" parent="pkg_auth" style="ellipse;whiteSpace=wrap;html=1;" value="Forgot / reset password" vertex="1">
          <mxGeometry height="60" width="210" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="pkg_events" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#d5e8d4;strokeColor=#82b366;" value="Events &amp; Catalog" vertex="1">
          <mxGeometry height="390" width="310" x="440" y="60" as="geometry" />
        </mxCell>
        <mxCell id="uc_browse" parent="pkg_events" style="ellipse;whiteSpace=wrap;html=1;" value="Browse published events" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_detail" parent="pkg_events" style="ellipse;whiteSpace=wrap;html=1;" value="View event detail (public / private rules)" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="uc_crud" parent="pkg_events" style="ellipse;whiteSpace=wrap;html=1;" value="Create / update / submit / delete event" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="178" as="geometry" />
        </mxCell>
        <mxCell id="uc_withdraw" parent="pkg_events" style="ellipse;whiteSpace=wrap;html=1;" value="Withdraw event from approval" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="248" as="geometry" />
        </mxCell>
        <mxCell id="uc_shortcode" parent="pkg_events" style="ellipse;whiteSpace=wrap;html=1;" value="Resolve event short code (check-in scanner)" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="318" as="geometry" />
        </mxCell>
        <mxCell id="pkg_setup" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#fff2cc;strokeColor=#d6b656;" value="Event Setup" vertex="1">
          <mxGeometry height="320" width="300" x="780" y="60" as="geometry" />
        </mxCell>
        <mxCell id="uc_cover" parent="pkg_setup" style="ellipse;whiteSpace=wrap;html=1;" value="Upload / presign cover image" vertex="1">
          <mxGeometry height="60" width="260" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_days" parent="pkg_setup" style="ellipse;whiteSpace=wrap;html=1;" value="Manage event days" vertex="1">
          <mxGeometry height="60" width="260" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="uc_prog_setup" parent="pkg_setup" style="ellipse;whiteSpace=wrap;html=1;" value="Manage programme / agenda slots" vertex="1">
          <mxGeometry height="60" width="260" x="20" y="178" as="geometry" />
        </mxCell>
        <mxCell id="uc_config" parent="pkg_setup" style="ellipse;whiteSpace=wrap;html=1;" value="Event module config (toggles)" vertex="1">
          <mxGeometry height="60" width="260" x="20" y="248" as="geometry" />
        </mxCell>
        <mxCell id="pkg_analytics" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#ffe6cc;strokeColor=#d79b00;" value="Analytics" vertex="1">
          <mxGeometry height="180" width="280" x="1110" y="60" as="geometry" />
        </mxCell>
        <mxCell id="uc_analytics" parent="pkg_analytics" style="ellipse;whiteSpace=wrap;html=1;" value="Event analytics dashboard" vertex="1">
          <mxGeometry height="60" width="240" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_analytics_export" parent="pkg_analytics" style="ellipse;whiteSpace=wrap;html=1;" value="Export analytics CSV" vertex="1">
          <mxGeometry height="60" width="240" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="pkg_orgdash" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#d5e8d4;strokeColor=#82b366;" value="Organizer Dashboard" vertex="1">
          <mxGeometry height="180" width="300" x="1420" y="60" as="geometry" />
        </mxCell>
        <mxCell id="uc_org_stats" parent="pkg_orgdash" style="ellipse;whiteSpace=wrap;html=1;" value="View organizer stats" vertex="1">
          <mxGeometry height="60" width="260" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_managers" parent="pkg_orgdash" style="ellipse;whiteSpace=wrap;html=1;" value="Invite / manage event managers" vertex="1">
          <mxGeometry height="60" width="260" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="pkg_sales" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#e1d5e7;strokeColor=#9673a6;" value="Tiers, Bookings, Tickets &amp; Payments" vertex="1">
          <mxGeometry height="460" width="310" x="160" y="490" as="geometry" />
        </mxCell>
        <mxCell id="uc_tiers" parent="pkg_sales" style="ellipse;whiteSpace=wrap;html=1;" value="Manage ticket tiers" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_book" parent="pkg_sales" style="ellipse;whiteSpace=wrap;html=1;" value="Create / view bookings" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="uc_cancel" parent="pkg_sales" style="ellipse;whiteSpace=wrap;html=1;" value="Cancel booking" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="178" as="geometry" />
        </mxCell>
        <mxCell id="uc_tickets" parent="pkg_sales" style="ellipse;whiteSpace=wrap;html=1;" value="List attendee tickets" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="248" as="geometry" />
        </mxCell>
        <mxCell id="uc_pay" parent="pkg_sales" style="ellipse;whiteSpace=wrap;html=1;" value="Initiate payment / Monnify webhook" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="318" as="geometry" />
        </mxCell>
        <mxCell id="uc_pay_verify" parent="pkg_sales" style="ellipse;whiteSpace=wrap;html=1;" value="Verify payment manually (fallback)" vertex="1">
          <mxGeometry height="60" width="270" x="20" y="388" as="geometry" />
        </mxCell>
        <mxCell id="pkg_ops" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#f8cecc;strokeColor=#b85450;" value="Check-in, Guests &amp; Engagement" vertex="1">
          <mxGeometry height="460" width="320" x="500" y="490" as="geometry" />
        </mxCell>
        <mxCell id="uc_checkin" parent="pkg_ops" style="ellipse;whiteSpace=wrap;html=1;" value="Check in tickets (QR / short code)" vertex="1">
          <mxGeometry height="60" width="280" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_checkin_invite" parent="pkg_ops" style="ellipse;whiteSpace=wrap;html=1;" value="Manage check-in staff invites" vertex="1">
          <mxGeometry height="60" width="280" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="uc_guest" parent="pkg_ops" style="ellipse;whiteSpace=wrap;html=1;" value="Guest list &amp; RSVP" vertex="1">
          <mxGeometry height="60" width="280" x="20" y="178" as="geometry" />
        </mxCell>
        <mxCell id="uc_pub_prog" parent="pkg_ops" style="ellipse;whiteSpace=wrap;html=1;" value="View public programme" vertex="1">
          <mxGeometry height="60" width="280" x="20" y="248" as="geometry" />
        </mxCell>
        <mxCell id="uc_comments" parent="pkg_ops" style="ellipse;whiteSpace=wrap;html=1;" value="Post / edit / like comments" vertex="1">
          <mxGeometry height="60" width="280" x="20" y="318" as="geometry" />
        </mxCell>
        <mxCell id="uc_ratings" parent="pkg_ops" style="ellipse;whiteSpace=wrap;html=1;" value="Rating forms &amp; responses" vertex="1">
          <mxGeometry height="60" width="280" x="20" y="388" as="geometry" />
        </mxCell>
        <mxCell id="pkg_comms" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#dae8fc;strokeColor=#6c8ebf;" value="Notifications, SSE &amp; Chat" vertex="1">
          <mxGeometry height="250" width="300" x="850" y="490" as="geometry" />
        </mxCell>
        <mxCell id="uc_notif" parent="pkg_comms" style="ellipse;whiteSpace=wrap;html=1;" value="In-app notifications" vertex="1">
          <mxGeometry height="60" width="260" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_sse" parent="pkg_comms" style="ellipse;whiteSpace=wrap;html=1;" value="Subscribe to live updates (SSE)" vertex="1">
          <mxGeometry height="60" width="260" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="uc_chat" parent="pkg_comms" style="ellipse;whiteSpace=wrap;html=1;" value="Direct messages (REST + WebSocket)" vertex="1">
          <mxGeometry height="60" width="260" x="20" y="178" as="geometry" />
        </mxCell>
        <mxCell id="pkg_budget" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#fff2cc;strokeColor=#d6b656;" value="Budget" vertex="1">
          <mxGeometry height="180" width="270" x="1180" y="490" as="geometry" />
        </mxCell>
        <mxCell id="uc_budget" parent="pkg_budget" style="ellipse;whiteSpace=wrap;html=1;" value="Track event budget &amp; alerts" vertex="1">
          <mxGeometry height="60" width="230" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_budget_items" parent="pkg_budget" style="ellipse;whiteSpace=wrap;html=1;" value="Manage budget line items" vertex="1">
          <mxGeometry height="60" width="230" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="pkg_contracts" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#f0e6ff;strokeColor=#7860a8;" value="Contracts &amp; Escrow" vertex="1">
          <mxGeometry height="320" width="330" x="160" y="1100" as="geometry" />
        </mxCell>
        <mxCell id="uc_contract_create" parent="pkg_contracts" style="ellipse;whiteSpace=wrap;html=1;" value="Create / update contracts (organizer)" vertex="1">
          <mxGeometry height="60" width="290" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_contract_sign" parent="pkg_contracts" style="ellipse;whiteSpace=wrap;html=1;" value="Sign / activate contract (vendor)" vertex="1">
          <mxGeometry height="60" width="290" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="uc_escrow" parent="pkg_contracts" style="ellipse;whiteSpace=wrap;html=1;" value="Fund escrow &amp; add milestones" vertex="1">
          <mxGeometry height="60" width="290" x="20" y="178" as="geometry" />
        </mxCell>
        <mxCell id="uc_milestone" parent="pkg_contracts" style="ellipse;whiteSpace=wrap;html=1;" value="Approve / release milestone funds" vertex="1">
          <mxGeometry height="60" width="290" x="20" y="248" as="geometry" />
        </mxCell>
        <mxCell id="pkg_vendor" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#e1d5e7;strokeColor=#9673a6;" value="Vendor Marketplace" vertex="1">
          <mxGeometry height="390" width="380" x="520" y="1100" as="geometry" />
        </mxCell>
        <mxCell id="uc_vendor_browse" parent="pkg_vendor" style="ellipse;whiteSpace=wrap;html=1;" value="Browse verified vendor marketplace" vertex="1">
          <mxGeometry height="60" width="340" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_vapp" parent="pkg_vendor" style="ellipse;whiteSpace=wrap;html=1;" value="Apply / manage vendor applications" vertex="1">
          <mxGeometry height="60" width="340" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="uc_vendor_profile" parent="pkg_vendor" style="ellipse;whiteSpace=wrap;html=1;" value="View vendor profile" vertex="1">
          <mxGeometry height="60" width="340" x="20" y="178" as="geometry" />
        </mxCell>
        <mxCell id="uc_vinq" parent="pkg_vendor" style="ellipse;whiteSpace=wrap;html=1;" value="Send / receive vendor inquiries" vertex="1">
          <mxGeometry height="60" width="340" x="20" y="248" as="geometry" />
        </mxCell>
        <mxCell id="uc_vendor_rate" parent="pkg_vendor" style="ellipse;whiteSpace=wrap;html=1;" value="Rate vendor post-event" vertex="1">
          <mxGeometry height="60" width="340" x="20" y="318" as="geometry" />
        </mxCell>
        <mxCell id="pkg_admin" parent="1" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=28;horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;fillColor=#f8cecc;strokeColor=#b85450;" value="Admin &amp; Staff Management" vertex="1">
          <mxGeometry height="390" width="340" x="930" y="1100" as="geometry" />
        </mxCell>
        <mxCell id="uc_admin_ev" parent="pkg_admin" style="ellipse;whiteSpace=wrap;html=1;" value="Approve / reject events &amp; edits" vertex="1">
          <mxGeometry height="60" width="300" x="20" y="38" as="geometry" />
        </mxCell>
        <mxCell id="uc_admin_u" parent="pkg_admin" style="ellipse;whiteSpace=wrap;html=1;" value="User / platform administration" vertex="1">
          <mxGeometry height="60" width="300" x="20" y="108" as="geometry" />
        </mxCell>
        <mxCell id="uc_admin_analytics" parent="pkg_admin" style="ellipse;whiteSpace=wrap;html=1;" value="Platform-wide analytics" vertex="1">
          <mxGeometry height="60" width="300" x="20" y="178" as="geometry" />
        </mxCell>
        <mxCell id="uc_admin_invite" parent="pkg_admin" style="ellipse;whiteSpace=wrap;html=1;" value="Admin invitation flow" vertex="1">
          <mxGeometry height="60" width="300" x="20" y="248" as="geometry" />
        </mxCell>
        <mxCell id="uc_admin_vendor" parent="pkg_admin" style="ellipse;whiteSpace=wrap;html=1;" value="Vendor verification moderation" vertex="1">
          <mxGeometry height="60" width="300" x="20" y="318" as="geometry" />
        </mxCell>
        <mxCell id="e_att_reg" edge="1" parent="1" source="act_attendee" style="endArrow=none;html=1;" target="uc_register">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_att_browse" edge="1" parent="1" source="act_attendee" style="endArrow=none;html=1;" target="uc_browse">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_att_book" edge="1" parent="1" source="act_attendee" style="endArrow=none;html=1;" target="uc_book">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_att_tickets" edge="1" parent="1" source="act_attendee" style="endArrow=none;html=1;" target="uc_tickets">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_att_notif" edge="1" parent="1" source="act_attendee" style="endArrow=none;html=1;" target="uc_notif">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_gst_browse" edge="1" parent="1" source="act_guest" style="endArrow=none;html=1;" target="uc_browse">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_gst_guest" edge="1" parent="1" source="act_guest" style="endArrow=none;html=1;" target="uc_guest">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_org_crud" edge="1" parent="1" source="act_org" style="endArrow=none;html=1;" target="uc_crud">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_org_cover" edge="1" parent="1" source="act_org" style="endArrow=none;html=1;" target="uc_cover">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_org_tiers" edge="1" parent="1" source="act_org" style="endArrow=none;html=1;" target="uc_tiers">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_org_analytics" edge="1" parent="1" source="act_org" style="endArrow=none;html=1;" target="uc_analytics">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_org_managers" edge="1" parent="1" source="act_org" style="endArrow=none;html=1;" target="uc_managers">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_org_budget" edge="1" parent="1" source="act_org" style="endArrow=none;html=1;" target="uc_budget">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_org_contract" edge="1" parent="1" source="act_org" style="endArrow=none;html=1;" target="uc_contract_create">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_mgr_checkin" edge="1" parent="1" source="act_mgr" style="endArrow=none;html=1;" target="uc_checkin">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_mgr_guest" edge="1" parent="1" source="act_mgr" style="endArrow=none;html=1;" target="uc_guest">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_mgr_prog" edge="1" parent="1" source="act_mgr" style="endArrow=none;html=1;" target="uc_pub_prog">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_staff_checkin" edge="1" parent="1" source="act_staff" style="endArrow=none;html=1;" target="uc_checkin">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_ven_browse" edge="1" parent="1" source="act_vendor" style="endArrow=none;html=1;" target="uc_vendor_browse">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_ven_vapp" edge="1" parent="1" source="act_vendor" style="endArrow=none;html=1;" target="uc_vapp">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_ven_vinq" edge="1" parent="1" source="act_vendor" style="endArrow=none;html=1;" target="uc_vinq">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_ven_sign" edge="1" parent="1" source="act_vendor" style="endArrow=none;html=1;" target="uc_contract_sign">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_adm_ev" edge="1" parent="1" source="act_admin" style="endArrow=none;html=1;" target="uc_admin_ev">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_adm_u" edge="1" parent="1" source="act_admin" style="endArrow=none;html=1;" target="uc_admin_u">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_adm_analytics" edge="1" parent="1" source="act_admin" style="endArrow=none;html=1;" target="uc_admin_analytics">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_adm_vendor" edge="1" parent="1" source="act_admin" style="endArrow=none;html=1;" target="uc_admin_vendor">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_mon_pay" edge="1" parent="1" source="act_ext" style="endArrow=none;html=1;dashed=1;" target="uc_pay">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
