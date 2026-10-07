-- Room for a real product description.
--
-- The column held 255 characters, so a description of a few bullet points
-- (fabric, weight, fit, print, sizes) was refused when the product was saved,
-- while the admin form itself allowed 500. 2000 is what the form now allows.

ALTER TABLE product ALTER COLUMN description TYPE VARCHAR(2000);
