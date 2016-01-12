LUPAPISTE.ConversationModel = function(params) {
  "use strict";
  var self = this;

  self.application = params.application;
  self.authorization = params.authorization;

  self.comment = comments.create();

  self.authorities = params.authorities;
  self.mainConversation = ko.observable(true);
  self.highlightConversation = ko.observable(false);

  self.currentPage = lupapisteApp.models.rootVMO.currentPage;
  var previousHash = lupapisteApp.models.rootVMO.previousHash;

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

  var previousPage = self.currentPage();

  function highlightConversation() {
    // TODO show comment panel if hidden
    self.comment.isSelected(true);
    self.highlightConversation(true);
    setTimeout(function() {
      self.highlightConversation(false);
    }, 2000);
  }

  ko.computed(function() {
    refreshConversations(self.currentPage());
    if (self.currentPage() !== previousPage && self.comment.text()) {
      hub.send("show-dialog", {ltitle: "application.conversation.unsentMessage.header",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "application.conversation.unsentMessage",
                                                 yesFn: function() {
                                                   if (previousHash()) {
                                                     location.hash = previousHash();
                                                   }
                                                   highlightConversation();
                                                 },
                                                 noFn: function() {
                                                   self.comment.text(undefined);
                                                   previousPage = self.currentPage();
                                                 },
                                                 lyesTitle: "application.conversation.sendMessage",
                                                 lnoTitle: "application.conversation.clearMessage"}});
    } else {
      previousPage = self.currentPage();
    }
  }).extend({ rateLimit: 100 });
};
