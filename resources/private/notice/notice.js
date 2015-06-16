LUPAPISTE.TagsDataProvider = function(filtered) {
  var self = this;

  self.query = ko.observable();
  self.filtered = ko.observableArray(filtered);

  var data = ["foo fat foo faa", "bar bati bar bar", "baz zzz"];
  self.data = ko.computed(function() {
    var filteredData = _.filter(data, function(item) {
      return !_.includes(self.filtered(), item);
    });
    var mappedData = _.map(filteredData, function(item) {
      return {label: item};
    });
    console.log("mappedData", mappedData);
    return mappedData;
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

  self.selectedTags = ko.observableArray();

  self.applicationTagsProvider = new LUPAPISTE.TagsDataProvider(self.selectedTags());


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

    subscriptions.push(self.selectedTags.subscribe(function(val) {
      // TODO persists tags
      console.log("selected tags", val);
      self.indicator({name: "tags", type: "saved"});
      self.applicationTagsProvider.filtered(val);
    }));
  };

  var unsubscribe = function() {
    while(subscriptions.length !== 0) {
      subscriptions.pop().dispose();
    }
  };

  subscribe();

  self.refresh = function(application) {
    console.log("refresh");
    // unsubscribe so that refresh does not trigger save
    unsubscribe();
    self.applicationId = application.id;
    self.urgency(application.urgency);
    self.authorityNotice(application.authorityNotice);
    self.selectedTags(["foo fat foo faa", "Live long and propel"]);
    subscribe();

    self.applicationTagsProvider = new LUPAPISTE.TagsDataProvider(self.selectedTags());
  };
};
