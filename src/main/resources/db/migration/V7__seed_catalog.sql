-- V7: starter catalog. Prices are indicative base prices in GBP.
-- Services refer to their category by slug, so this script doesn't depend on generated ids.
INSERT INTO categories (slug, name, description, icon, sort_order) VALUES
    ('cleaning',         'Cleaning',              'Regular, deep, and end-of-tenancy cleaning.',     'sparkles',        10),
    ('plumbing',         'Plumbing',              'Leaks, taps, toilets, and boiler servicing.',     'wrench',          20),
    ('electrical',       'Electrical',            'Sockets, lighting, and safety checks.',           'bolt',            30),
    ('salon-at-home',    'Salon at home',         'Haircuts, styling, and nails in your home.',      'scissors',        40),
    ('appliance-repair', 'Appliance repair',      'Washing machines, fridges, and ovens.',           'washing-machine', 50),
    ('painting',         'Painting & decorating', 'Rooms, feature walls, and touch-ups.',            'paint-roller',    60);

INSERT INTO services (category_id, slug, name, description, base_price, duration_minutes)
SELECT c.id, s.slug, s.name, s.description, s.base_price, s.duration_minutes
FROM (VALUES
    ('cleaning',         'standard-home-clean',    'Standard home clean',            'Dusting, vacuuming, mopping, kitchen and bathroom surfaces.',          60.00, 120),
    ('cleaning',         'deep-clean',             'Deep clean',                     'Top-to-bottom clean including inside cupboards, skirting, and grout.', 150.00, 240),
    ('cleaning',         'end-of-tenancy-clean',   'End-of-tenancy clean',           'Checklist clean to help you get your deposit back.',                   220.00, 360),
    ('cleaning',         'oven-clean',             'Oven clean',                     'Degreasing of oven, racks, and hob.',                                  55.00,  90),
    ('plumbing',         'leak-repair',            'Leak repair',                    'Find and fix a leak under a sink, bath, or appliance.',                75.00,  60),
    ('plumbing',         'tap-replacement',        'Tap replacement',                'Remove an old tap and fit a new one you supply.',                      65.00,  60),
    ('plumbing',         'toilet-repair',          'Toilet repair',                  'Fix running, leaking, or blocked toilets.',                            70.00,  60),
    ('plumbing',         'boiler-service',         'Boiler service',                 'Annual gas boiler service by a Gas Safe engineer.',                    90.00,  60),
    ('electrical',       'socket-installation',    'Socket installation',            'Add or replace a power socket.',                                       60.00,  60),
    ('electrical',       'light-fitting',          'Light fitting',                  'Install or replace a ceiling or wall light.',                          55.00,  60),
    ('electrical',       'electrical-safety-check','Electrical safety check (EICR)', 'Full inspection with a certificate for landlords and buyers.',         150.00, 180),
    ('salon-at-home',    'womens-haircut',         'Women''s haircut',               'Wash, cut, and blow-dry.',                                             45.00,  60),
    ('salon-at-home',    'mens-haircut',           'Men''s haircut',                 'Cut and style.',                                                       25.00,  30),
    ('salon-at-home',    'manicure',               'Manicure',                       'Shape, cuticle care, and polish.',                                     30.00,  45),
    ('appliance-repair', 'washing-machine-repair', 'Washing machine repair',         'Diagnose and repair. Parts are charged separately.',                   85.00,  90),
    ('appliance-repair', 'fridge-repair',          'Fridge repair',                  'Diagnose and repair. Parts are charged separately.',                   90.00,  90),
    ('painting',         'room-painting',          'Paint a room',                   'Walls and ceiling of one average-sized room, two coats.',              250.00, 480),
    ('painting',         'feature-wall',           'Feature wall',                   'One wall in a colour or finish of your choice.',                       120.00, 240)
) AS s (category_slug, slug, name, description, base_price, duration_minutes)
JOIN categories c ON c.slug = s.category_slug;
