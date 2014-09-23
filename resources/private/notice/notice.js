LUPAPISTE.NoticeModel = function() {
  "use strict";

  var self = this;

  self.applicationId = null;
  self.authorityNotice = ko.observable();
  self.urgency = ko.observable('normal');

  self.showSaveIndicator = ko.observable(false);

  self.availableUrgencyStates = ko.observableArray(['normal', 'urgent', 'pending']);

  var subscriptions = [];

  var showIndicator = function() {
    self.showSaveIndicator(true);
    setTimeout(function() {
      self.showSaveIndicator(false);
    }, 4000);
  }

  var subscribe = function() {
    subscriptions.push(self.urgency.subscribe(_.debounce(function(value) {
      ajax
        .command("change-urgency", {
          id: self.applicationId,
          urgency: value})
        .success(showIndicator)
        .call();
    }, 500)));

    subscriptions.push(self.authorityNotice.subscribe(_.debounce(function(value) {
      ajax
        .command("add-authority-notice", {
          id: self.applicationId,
          authorityNotice: value})
        .success(showIndicator)
        .call();
    }, 500)));
  };

  var unsubscribe = function() {
    while(subscriptions.length != 0) {
      subscriptions.pop().dispose();
    }
  }

  subscribe();

  self.refresh = function(application) {
    // unsubscribe so that refresh does not trigger save
    unsubscribe();
    self.applicationId = application.id;
    self.urgency(application.urgency);
    self.authorityNotice(application.authorityNotice);
    subscribe();
  };
};
