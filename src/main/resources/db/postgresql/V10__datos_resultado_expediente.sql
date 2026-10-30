ALTER TABLE expedientes_sunarp ADD COLUMN placa_resultado VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE expedientes_sunarp ADD COLUMN partida_resultado VARCHAR(50) NOT NULL DEFAULT '';
UPDATE expedientes_sunarp e SET placa_resultado=m.placa,partida_resultado=m.partida FROM motos_importadas m WHERE e.moto_id=m.id AND e.estado='INSCRITO';
