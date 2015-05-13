LUPAPISTE.InvoiceOperatorSelectorModel = function(params) {
  "use strict";
  var self = this;

  self.operators = _(LUPAPISTE.config.eInvoiceOperators).map(function(operator) {
      var name = loc("operator." + operator) + " (" + operator + ")";
      return {
        name: name,
        code: operator
      }
    }).sortBy(function(operator) {
      return operator.name;
    }).value();

  self.selected = params.selected || ko.observable();
  self.enabled = params.enabled || true;
  self.required = params.required || false;
};
