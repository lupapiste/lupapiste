LUPAPISTE.CellInvoiceOperatorModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.CellModel( params ));

  var oldModel = new LUPAPISTE.InvoiceOperatorSelectorModel({});

  self.operators = oldModel.operators;
  self.setOptionDisable = oldModel.setOptionDisable;

};
