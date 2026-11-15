-- Crew (coded IDs and sex from the OSD-575 ISA metadata).
INSERT INTO participant (id, sex, display_name) VALUES
    ('C001', 'Male', 'Crew C001'),
    ('C002', 'Female', 'Crew C002'),
    ('C003', 'Female', 'Crew C003'),
    ('C004', 'Male', 'Crew C004');

-- Fixed guidance knowledge base, paraphrased from NASA public pages.
-- These are software suggestions, NOT diagnoses or medical prescriptions.
INSERT INTO guidance (code, title, body, source_name, source_url) VALUES
    ('BIO_ATTENTION',
     'Unusual serum biomarker pattern',
     'Compare this sample with your earlier ones and note any symptoms. Spaceflight can alter the heart and blood vessels, including blood-clotting and inflammation markers. Keep up your exercise and fluid routine and raise the result at your next scheduled medical conference.',
     'NASA - Risk of Cardiovascular Adaptations',
     'https://www.nasa.gov/reference/risk-of-cardiovascular-adaptations/'),
    ('BIO_REVIEW',
     'Biomarker pattern needs review',
     'Several biomarkers moved strongly away from your own pre-flight baseline. Share this result with the crew medical officer or flight surgeon and follow mission medical procedures. Do not change medication or training on the basis of this notification alone.',
     'NASA - Risk of Cardiovascular Adaptations',
     'https://www.nasa.gov/reference/risk-of-cardiovascular-adaptations/'),
    ('VIT_INFLIGHT',
     'Heart rate away from your resting baseline (in flight)',
     'Fluid shifts toward the head and chest in microgravity, and the heart and vessels adapt. Re-measure at rest in 10 minutes. Keep your scheduled exercise (resistive device, treadmill, cycle), which is the main countermeasure against cardiovascular deconditioning.',
     'NASA - Cardiovascular Health in Microgravity',
     'https://www.nasa.gov/missions/station/cardiovascular-health-in-microgravity/'),
    ('VIT_POSTLANDING',
     'Elevated heart rate after landing',
     'After return, lower blood volume can cause light-headedness or fainting when standing (orthostatic intolerance). Drink fluids, wear compression garments if provided, rise slowly and sit or lie down if you feel faint. Report persistent symptoms to the medical team.',
     'NASA - Cardiovascular Health in Microgravity; Risk of Cardiovascular Adaptations',
     'https://www.nasa.gov/missions/station/cardiovascular-health-in-microgravity/'),
    ('VIT_GENERAL',
     'Heart rate or HRV away from your resting baseline',
     'Re-measure at rest in 10 minutes and note activity, sleep and hydration. If the change persists, log it for the medical team.',
     'NASA - Cardiovascular Health in Microgravity',
     'https://www.nasa.gov/missions/station/cardiovascular-health-in-microgravity/'),
    ('DQ_BATCH',
     'Possible data-quality issue',
     'Most of the crew shifted strongly at the same sampling time. That pattern usually points to sample handling or a laboratory batch effect rather than individual physiology. Verify the samples before interpreting this result.',
     'NASA OSDR - OSD-575 study metadata',
     'https://osdr.nasa.gov/bio/repo/data/studies/OSD-575');
