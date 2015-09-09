LUPAPISTE.AutocompleteHandlersModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selectedValues;
  self.query = ko.observable("");

  var data = ko.observable();
  var defaultFilter = ko.pureComputed(function() {
    return util.getIn(lupapisteApp.models.currentUser, ["applicationFilters", 0, "filter", "handler"]);
  });

  function mapUser(user) {
    var fullName = "";
    if (user) {
      if (user.lastName) { fullName += user.lastName; }
      if (user.firstName && user.lastName) { fullName += "\u00a0"; }
      if (user.firstName) { fullName +=  user.firstName; }
    }
    user.fullName = fullName;
    return user;
  }

  ko.computed(function() {
    self.selected(_.findWhere(data(), {id: defaultFilter()}));
  });

  ajax
    .query("users-in-same-organizations")
    .error(_.noop)
    .success(function(res) {
      data(_(res.users).map(mapUser).sortBy("fullName").value());
    })
    .call();

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _(data())
      .filter(function(item) {
        return _.reduce(q.split(" "), function(result, word) {
          return _.contains(item.fullName.toUpperCase(), word.toUpperCase()) && result;
        }, true);
      })
      .sortBy("label")
      .value();
  });
};
