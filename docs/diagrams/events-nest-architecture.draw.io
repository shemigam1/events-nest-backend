<mxfile host="app.diagrams.net" agent="events-nest">
  <diagram id="arch-1" name="System architecture">
    <mxGraphModel dx="1288" dy="703" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1800" pageHeight="1200" math="0" shadow="0">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
        <mxCell id="title" parent="1" style="text;html=1;strokeColor=none;fillColor=none;align=left;fontSize=18;fontStyle=1" value="Events Nest Server — deployment-style architecture" vertex="1">
          <mxGeometry height="40" width="800" x="40" y="20" as="geometry" />
        </mxCell>
        <mxCell id="client" parent="1" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=12;fontStyle=1" value="Clients&#xa;Web / mobile" vertex="1">
          <mxGeometry height="70" width="160" x="80" y="120" as="geometry" />
        </mxCell>
        <mxCell id="lb" parent="1" style="shape=parallelogram;perimeter=parallelogramPerimeter;whiteSpace=wrap;html=1;fixedSize=1;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;" value="Reverse proxy&#xa;(TLS, X-Forwarded-For)&#xa;optional" vertex="1">
          <mxGeometry height="60" width="200" x="300" y="125" as="geometry" />
        </mxCell>
        <mxCell id="app" parent="1" style="rounded=0;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacingLeft=10;spacingTop=8;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;" value="Spring Boot — events-nest-server&#xa;&#xa;• REST Controllers  (JWT + @PreAuthorize)&#xa;• WebSocket STOMP /ws  (chat · per-user /queue/notify)&#xa;• Security: JWT filter, RateLimitFilter (Redis / Bucket4j)&#xa;• Domain services + JPA repositories  (Flyway migrations)&#xa;• Kafka producers / consumers  (6 event topics, 2 groups)&#xa;• SSE  (SseEmitter — live booking / event / check-in updates)&#xa;• Email outbox: EmailJob table → scheduler poller → Resend&#xa;• S3 / local file storage  (presigned uploads; local = dev fallback)&#xa;• CalendarService  (Google Cal URL + iCal on-demand, no DB write)" vertex="1">
          <mxGeometry height="270" width="460" x="560" y="90" as="geometry" />
        </mxCell>
        <mxCell id="pg" parent="1" style="shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;size=15;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=11;fontStyle=1" value="PostgreSQL&#xa;primary data store" vertex="1">
          <mxGeometry height="100" width="140" x="1110" y="100" as="geometry" />
        </mxCell>
        <mxCell id="redis" parent="1" style="shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;size=15;fillColor=#f8cecc;strokeColor=#b85450;fontSize=11;fontStyle=1" value="Redis&#xa;• Spring Cache (JSON)&#xa;• Bucket4j rate limits" vertex="1">
          <mxGeometry height="115" width="160" x="1110" y="245" as="geometry" />
        </mxCell>
        <mxCell id="kafka" parent="1" style="shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;size=15;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;fontStyle=1" value="Apache Kafka — 6 domain event topics&#xa;&#xa;ticket.checked-in  ·  booking.confirmed&#xa;event.approved  ·  event.rejected&#xa;contract.signed  ·  audit.events&#xa;&#xa;Consumer groups:&#xa;  events-nest-notifications&#xa;  audit-consumer-group" vertex="1">
          <mxGeometry height="165" width="260" x="560" y="420" as="geometry" />
        </mxCell>
        <mxCell id="resend" parent="1" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#ffe6cc;strokeColor=#d79b00;fontSize=11;fontStyle=1" value="Resend&#xa;(transactional email)" vertex="1">
          <mxGeometry height="70" width="160" x="860" y="440" as="geometry" />
        </mxCell>
        <mxCell id="s3" parent="1" style="shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;size=15;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=11;fontStyle=1" value="Object storage&#xa;S3-compatible&#xa;(covers, assets)" vertex="1">
          <mxGeometry height="100" width="160" x="1110" y="440" as="geometry" />
        </mxCell>
        <mxCell id="monnify" parent="1" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;fontSize=11;fontStyle=1" value="Monnify&#xa;payments + webhooks&#xa;(HMAC verified)" vertex="1">
          <mxGeometry height="80" width="200" x="560" y="620" as="geometry" />
        </mxCell>
        <mxCell id="e_c_lb" edge="1" parent="1" source="client" style="endArrow=block;html=1;exitX=1;exitY=0.5;entryX=0;entryY=0.5;" target="lb">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_lb_app" edge="1" parent="1" source="lb" style="endArrow=block;html=1;exitX=1;exitY=0.5;entryX=0;entryY=0.25;" target="app">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_app_pg" edge="1" parent="1" source="app" style="endArrow=block;html=1;exitX=1;exitY=0.2;entryX=0;entryY=0.5;" target="pg">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_app_red" edge="1" parent="1" source="app" style="endArrow=block;html=1;exitX=1;exitY=0.65;entryX=0;entryY=0.35;" target="redis">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_app_kaf" edge="1" parent="1" source="app" style="endArrow=block;startArrow=block;html=1;exitX=0.3;exitY=1;entryX=0.3;entryY=0;" target="kafka">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_app_res" edge="1" parent="1" source="app" style="endArrow=open;dashed=1;html=1;" target="resend">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="940" y="390" />
              <mxPoint x="940" y="440" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="e_app_s3" edge="1" parent="1" source="app" style="endArrow=open;dashed=1;html=1;exitX=1;exitY=0.9;entryX=0;entryY=0.5;" target="s3">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="e_mon_app" edge="1" parent="1" source="monnify" style="endArrow=open;dashed=1;startArrow=block;html=1;exitX=0.5;exitY=0;entryX=0.3;entryY=1;" target="app">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="note" parent="1" style="shape=note;whiteSpace=wrap;html=1;size=14;fillColor=#ffffc0;strokeColor=#d6b656;align=left;fontSize=10;" value="Observability: Actuator health / metrics / Prometheus as configured.&#xa;Scale: multiple app instances share Redis (cache + rate limit) and Kafka consumer groups.&#xa;Storage fallback: LocalFileStorageService active when storage.type=local (dev / CI)." vertex="1">
          <mxGeometry height="75" width="430" x="80" y="420" as="geometry" />
        </mxCell>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
