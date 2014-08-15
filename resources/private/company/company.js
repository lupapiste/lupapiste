(function() {
  "use strict";

  function Company() {
    var self = this;

    self.pending = ko.observable();
    self.id = ko.observable();
    self.name = ko.observable();
    self.y = ko.observable();
    self.users = ko.observableArray();

    self.clear = function() {
      _(self).values().filter(ko.isObservable).each(function(o) { o(null); });
      return self;
    };

    self.update = function(data) {
      return self
        .name(data.company.name)
        .y(data.company.y)
        .users(data.users);
    };

    self.load = function() {
      ajax
        .query("company", {company: self.id(), users: true})
        .pending(self.pending)
        .success(self.update)
        .call();
      return self;
    };

    self.init = function(id) {
      return (self.id() === id) ? self : self.clear().id(id).load();
    };
  }

  var company = new Company();

  hub.onPageChange("company", function(e) { company.init(e.pagePath[0]); });

  $(function() {
    $("#company").applyBindings(company);
  });

})();
