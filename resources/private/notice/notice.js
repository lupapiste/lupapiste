LUPAPISTE.OrganizationTagsDataProvider = function(organization, filtered) {
  "use strict";

  var self = this;

  self.query = ko.observable();

  self.filtered = filtered || ko.observableArray([]);

  var data = ko.observable();

  if (organization && util.getIn(lupapisteApp.models.currentUser, ["orgAuthz", organization])) {
    ajax
      .query("get-organization-tags", {organizationId: organization})
      .error(_.noop)
      .success(function(res) {
        data(res.tags);
      })
      .call();
  }

  self.data = ko.pureComputed(function() {
    var filteredData = _.filter(data(), function(tag) {
      return !_.some(self.filtered(), tag);
    });
    var q = self.query() || "";
    filteredData = _.filter(filteredData, function(tag) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(tag.label.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
    return filteredData;
  });
};

LUPAPISTE.ApplicationTagsDataProvider = function(application, filtered) {
  "use strict";

  var self = this;

  self.query = ko.observable();

  self.filtered = filtered || ko.observableArray([]);

  var data = ko.observable(_.map(application.organizationMeta.tags, function(k, v) {
      return {id: v, label: k};
    }));

  self.data = ko.pureComputed(function() {
    var filteredData = _.filter(data(), function(tag) {
      return !_.some(self.filtered(), tag);
    });
    var q = self.query() || "";
    filteredData = _.filter(filteredData, function(tag) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(tag.label.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
    return filteredData;
  });
};

LUPAPISTE.NoticeModel = function() {
  "use strict";

  var self = this;

  self.applicationId = null;
  self.authorityNotice = ko.observable();
  self.urgency = ko.observable("normal");

  self.indicator = ko.observable({name: undefined, type: undefined}).extend({notify: "always"});

  self.availableUrgencyStates = ko.observableArray(["normal", "urgent", "pending"]);

  self.selectedTags = ko.observableArray([]);

  self.applicationTagsProvider = null;

  var subscriptions = [];

  var subscribe = function() {
    subscriptions.push(self.urgency.subscribe(_.debounce(function(value) {
      ajax
        .command("change-urgency", {
          id: self.applicationId,
          urgency: value})
        .success(function() {
          self.indicator({name: "urgency", type: "saved"});
        })
        .error(function() {
          self.indicator({name: "urgency", type: "err"});
        })
        .call();
    }, 500)));

    subscriptions.push(self.authorityNotice.subscribe(_.debounce(function(value) {
      ajax
        .command("add-authority-notice", {
          id: self.applicationId,
          authorityNotice: value})
        .success(function() {
          self.indicator({name: "notice", type: "saved"});
        })
        .error(function() {
          self.indicator({name: "urgency", type: "err"});
        })
        .call();
    }, 500)));

    subscriptions.push(self.selectedTags.subscribe(_.debounce(function(tags) {
      ajax
        .command("add-application-tags", {
          id: self.applicationId,
          tags: _.pluck(tags, "id")})
        .success(function() {
          self.indicator({name: "tags", type: "saved"});
        })
        .error(function() {
          self.indicator({name: "tags", type: "err"});
        })
        .call();
    }, 500)));
  };

  var unsubscribe = function() {
    while(subscriptions.length !== 0) {
      subscriptions.pop().dispose();
    }
  };

  subscribe();

  self.refresh = function(application) {
    // unsubscribe so that refresh does not trigger save
    unsubscribe();
    self.applicationId = application.id;
    self.urgency(application.urgency);
    self.authorityNotice(application.authorityNotice);
    self.selectedTags(application.tags);
    self.applicationTagsProvider = new LUPAPISTE.ApplicationTagsDataProvider(application, self.selectedTags);
    subscribe();
  };
};
