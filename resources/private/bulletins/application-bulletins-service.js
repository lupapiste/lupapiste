LUPAPISTE.ApplicationBulletinsService = function() {
  "use strict";
  var self = this;

  self.data = ko.observableArray([]);
  self.bulletinsLeft = ko.observable(0);

  self.fetchBulletins = _.debounce(function (query, pending) {
    ajax.datatables("application-bulletins", {page:         query.page,
                                              searchText:   util.getIn(query, ["searchText"],         ""),
                                              municipality: util.getIn(query, ["municipality", "id"], ""),
                                              state:        util.getIn(query, ["state", "id"],        "")})
      .success(function(res) {
        self.bulletinsLeft(res.left);
        if (query.page === 1) {
          self.data(res.data);
        } else {
          self.data(self.data().concat(res.data));
        }
      })
      .pending(pending || _.noop)
      .call();
  }, 250);

  self.municipalities = ko.observableArray([]);
  self.fetchMunicipalities = function() {
    ajax.query("application-bulletin-municipalities", {})
      .success(function(res) {
        self.municipalities(res.municipalities);
      })
      .call();
  };

  self.states = ko.observableArray([]);
  self.fetchStates = function() {
    ajax.query("application-bulletin-states", {})
      .success(function(res) {
        self.states(res.states);
      })
      .call();
  };
};

