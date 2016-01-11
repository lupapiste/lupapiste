LUPAPISTE.ConversationModel = function(params) {
  "use strict";
  var self = this;

  self.application = params.application;
  self.authorization = params.authorization;

  self.comment = comments.create();

  self.authorities = [];
  self.mainConversation = ko.observable(true);
  self.currentPage = ko.observable();

  self.infoRequest = ko.pureComputed(function() {
    return util.getIn(self, ["application", "infoRequest"]);
  });

  self.applicationId = ko.computed(function() {
    return util.getIn(self, ["application", "id"]);
  });

  function refreshConversations(page) {
    if (page) {
      var type = pageutil.getPage();
      self.mainConversation(true);
      switch(type) {
        case "attachment":
          self.mainConversation(false);
          self.comment.refresh(self.application, false, {type: type, id: pageutil.lastSubPage()});
          break;
        case "statement":
          self.mainConversation(false);
          self.comment.refresh(self.application, false, {type: type, id: pageutil.lastSubPage()});
          break;
        case "verdict":
          self.mainConversation(false);
          self.comment.refresh(self.application, false, {type: type, id: pageutil.lastSubPage()}, ["authority"]);
          break;
        default:
          self.comment.refresh(self.application, true);
          break;
      }
    }
  }

  ko.computed(function() {
    refreshConversations(self.currentPage());
  }).extend({ rateLimit: 100 });

  var pageLoadSubscription = hub.subscribe({type: "page-load"}, function(data) {
    // TODO check if there is unsent message when page changes
    self.currentPage(pageutil.getPage());
  });

  self.dispose = function() {
    hub.unsubscribe(pageLoadSubscription);
  };
};
