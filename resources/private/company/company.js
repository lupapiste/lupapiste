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

    self.show = function(id) {
      return (self.id() === id) ? self : self.clear().id(id).load();
    };

    self.op = function(m, e) {
      var target = $(e.target),
          userId = target.attr("data-userId"),
          op = target.attr("data-op");
      // TODO:
      console.log("userId:", userId, "op:", op);
    };
  }

  var company = new Company();

  hub.onPageChange("company", function(e) { company.show(e.pagePath[0]); });

  $(function() {
    $("#company").applyBindings(company);
  });

})();
