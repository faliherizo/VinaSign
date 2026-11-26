--liquibase formatted sql

--changeset vinasign:036-1-etat-abonnement
--comment: VS-121 — libellés des états d'abonnement affichés à l'écran « Facturation » du portail (les codes restent ceux de la machine d'états du domaine).
INSERT INTO referentiel_type (code, libelles) VALUES ('etat-abonnement', '{"fr":"État de l''abonnement","en":"Subscription status"}');
INSERT INTO referentiel_valeur (type_id, code, libelles, ordre, attributs)
SELECT t.id, v.code, v.libelles::jsonb, v.ordre, '{}'::jsonb FROM referentiel_type t JOIN (VALUES
  ('actif',   '{"fr":"Actif","en":"Active"}', 1),
  ('impaye',  '{"fr":"Paiement en retard","en":"Payment overdue"}', 2),
  ('termine', '{"fr":"Terminé","en":"Ended"}', 3)
) AS v(code, libelles, ordre) ON t.code = 'etat-abonnement';
--rollback DELETE FROM referentiel_valeur WHERE type_id = (SELECT id FROM referentiel_type WHERE code = 'etat-abonnement'); DELETE FROM referentiel_type WHERE code = 'etat-abonnement';
