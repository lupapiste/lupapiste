// Params:
// drawings: Observable array of drawings
// [ltitle]: Localization key for table caption
// [deleteFn]: Event handler for delete. Delete column only shown when
// handler is given.
LUPAPISTE.DrawingsTableModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.drawings = params.drawings;
  self.ltitle = params.ltitle;
  self.deleteFn = params.deleteFn;

};
