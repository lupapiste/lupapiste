LUPAPISTE.InvoiceOperatorSelectorModel = function(params) {
  "use strict";
  var self = this;

  self.operators = _(LUPAPISTE.config.eInvoiceOperators).map(function(operator) {
    var name = loc("osapuoli.yritys.verkkolaskutustieto.valittajaTunnus." + operator.name);
    return {
      name: name,
      code: operator.name,
      disabled: operator.disabled
    }
  }).sortBy(function(operator) {
    return operator.name;
  }).value();

  self.setOptionDisable = function(option, item) {
    ko.applyBindingsToNode(option, {disable: item.disabled}, item);
  }

  self.selected = params.selected || ko.observable();
  self.enabled = params.enabled || true;
  self.required = params.required || false;
};
