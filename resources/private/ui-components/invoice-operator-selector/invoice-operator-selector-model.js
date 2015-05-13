LUPAPISTE.InvoiceOperatorSelectorModel = function(params) {
  "use strict";
  var self = this;

  var operators = _(LUPAPISTE.config.eInvoiceOperators).map(function(operator) {
      var name = loc("operator." + operator) + " (" + operator + ")";
      return {
        name: name,
        code: operator
      }
    }).sortBy(function(operator) {
      return operator.name;
    }).value();

  self.operators = ko.observableArray(operators);
  self.selected = params.selected;
};
