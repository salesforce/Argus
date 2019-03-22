INSERT INTO principals (name) VALUES ('GG`default');

INSERT INTO policies (namespace, name, duration, threshold, action, scope) VALUES ('argus', 'defaultGroupPerMinute', 60, 200000, 'SLOWDOWN', 'OVERALL');

INSERT INTO principal_policy (principal_id, policy_id) VALUES
( (SELECT principal_id FROM principals WHERE name = 'GG`default'),
  (SELECT policy_id FROM policies WHERE name = 'defaultGroupPerMinute')
);

INSERT INTO principals (name) VALUES ('GG`group5');

INSERT INTO policies (namespace, name, duration, threshold, action, scope) VALUES ('argus', 'group5PerMinuteWARN', 60, 15000000, 'WARN', 'OVERALL');

INSERT INTO principal_policy (principal_id, policy_id) VALUES
( (SELECT principal_id FROM principals WHERE name = 'GG`group5'),
  (SELECT policy_id FROM policies WHERE name = 'group5PerMinuteWARN')
);

INSERT INTO policies (namespace, name, duration, threshold, action, scope) VALUES ('argus', 'group5PerMinuteSLOWDOWN', 60, 150000000, 'SLOWDOWN', 'OVERALL');

INSERT INTO principal_policy (principal_id, policy_id) VALUES
( (SELECT principal_id FROM principals WHERE name = 'GG`group5'),
  (SELECT policy_id FROM policies WHERE name = 'group5PerMinuteSLOWDOWN')
);



INSERT INTO principals (name) VALUES ('SD`default`default');

INSERT INTO policies (namespace, name, duration, threshold, action, scope) VALUES ('argus', 'defaultServiceDCPerMinute', 60, 2000000, 'WARN', 'OVERALL');
INSERT INTO principal_policy (principal_id, policy_id) VALUES
( (SELECT principal_id FROM principals WHERE name = 'SD`default`default'),
  (SELECT policy_id FROM policies WHERE name = 'defaultServiceDCPerMinute')
);

INSERT INTO policies (namespace, name, duration, threshold, action, scope) VALUES ('argus', 'defaultServiceDCPerHour', 3600, 60000000, 'WARN', 'OVERALL');
INSERT INTO principal_policy (principal_id, policy_id) VALUES
( (SELECT principal_id FROM principals WHERE name = 'SD`default`default'),
  (SELECT policy_id FROM policies WHERE name = 'defaultServiceDCPerHour')
);

