LUPAPISTE.SidePanelModel = function() {
  "use strict";
  var self = this;

  self.typeId = undefined;

  self.application = ko.observable();
  self.applicationId = ko.observable();
  self.notice = ko.observable({});
  self.attachmentId = ko.observable();
  if (LUPAPISTE.NoticeModel) {
    self.notice(new LUPAPISTE.NoticeModel());
  }
  self.showConversationPanel = ko.observable(false);
  self.showNoticePanel = ko.observable(false);
  self.unseenComments = ko.observable();
  self.authorization = lupapisteApp.models.applicationAuthModel;
  self.comment = ko.observable(comments.create());
  self.permitType = ko.observable();
  self.authorities = ko.observableArray([]);
  self.infoRequest = ko.observable();
  self.authentication = ko.observable();
  self.authorities = ko.observable();
  self.mainConversation = ko.observable(true);
  self.showHelp = ko.observable(false);

  self.sidePanelVisible = ko.computed(function() {
    return self.showConversationPanel() || self.showNoticePanel();
  });

  self.previousPage = undefined;

  var AuthorityInfo = function(id, firstName, lastName) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
  };

  function initAuthoritiesSelectList(data) {
    var authorityInfos = [];
    _.each(data || [], function(authority) {
      authorityInfos.push(new AuthorityInfo(authority.id, authority.firstName, authority.lastName));
    });
    self.authorities(authorityInfos);
  }

  self.refreshConversations = function(application) {
    if (application) {
      var type = pageutil.getPage();
      self.mainConversation(true);
      switch(type) {
        case "attachment":
          self.mainConversation(false);
          self.comment().refresh(application, false, {type: type, id: pageutil.lastSubPage()});
          break;
        case "statement":
          self.comment().refresh(application, false, {type: type, id: pageutil.lastSubPage()});
          break;
        case "verdict":
          self.comment().refresh(application, false, {type: type, id: pageutil.lastSubPage()}, ["authority"]);
          break;
        default:
          self.comment().refresh(application, true);
          break;
      }
    }
  };

  self.refresh = function(application, authorities) {
    // TODO applicationId, inforequest etc. could be computed
    if (application && authorities) {
      self.application(application);
      self.authorities(authorities);
      self.applicationId(application.id);
      self.infoRequest(application.infoRequest);
      self.unseenComments(application.unseenComments);
      if (self.notice().refresh) {
        self.notice().refresh(application);
      }
      self.permitType(self.application().permitType);
      initAuthoritiesSelectList(self.authorities());
    }
    self.refreshConversations(self.application());
  };

  self.showConversationPanel.subscribe(function(value) {
    hub.send(value ? "side-panel-open" : "side-panel-close");
  });

  self.toggleConversationPanel = function() {
    self.showConversationPanel(!self.showConversationPanel());
    self.showNoticePanel(false);
    // Set focus to new comment textarea
    self.comment().isSelected(self.showConversationPanel());

    if (self.showConversationPanel()) {
      setTimeout(function() {
        // Mark comments seen after a second
        if (self.applicationId() && self.authorization.ok("mark-seen")) {
          ajax.command("mark-seen", {id: self.applicationId(), type: "comments"})
          .success(function() {self.unseenComments(0);})
          .call();
        }}, 1000);
    }
  };

  self.highlightConversation = function() {
    if (!self.showConversationPanel()) {
      self.toggleConversationPanel();
    } else {
      self.comment().isSelected(true);
    }
    $("#conversation-panel").addClass("highlight-conversation");
    setTimeout(function() {
      $("#conversation-panel").removeClass("highlight-conversation");
    }, 2000);
  };

  self.toggleNoticePanel = function() {
    self.showNoticePanel(!self.showNoticePanel());
    self.showConversationPanel(false);
  };

  self.closeSidePanel = function() {
    if (self.showConversationPanel()) {
      self.toggleConversationPanel();
    }
    if (self.showNoticePanel()) {
      self.toggleNoticePanel();
    }
  };

  self.toggleHelp = function() {
    self.showHelp(!self.showHelp());
  };

  self.hideHelp = function() {
    self.showHelp(false);
  };

  var pages = ["application","attachment","statement","neighbors","verdict"];
  var unsentMessage = false;

  var refreshSidePanel = function(previousHash) {
    var currentPage = pageutil.getPage();
    if (self.previousPage && currentPage !== self.previousPage && self.comment().text()) {
      unsentMessage = true;
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("application.conversation.unsentMessage.header"),
        loc("application.conversation.unsentMessage"),
        {title: loc("application.conversation.sendMessage"), fn: function() {
          if (previousHash) {
            location.hash = previousHash;
          }
          unsentMessage = false;
          self.highlightConversation();
        }},
        {title: loc("application.conversation.clearMessage"), fn: function() {
          self.comment().text(undefined);
          self.refresh();
          unsentMessage = false;
          self.previousPage = currentPage;
        }}
      );
    } else if (!unsentMessage) {
      self.refresh();
      self.previousPage = currentPage;
    }
  };

  hub.subscribe({type: "dialog-close"}, function(data) {
    // Application error occurred
    if (data.id === "dialog-application-load-error") {
      self.comment().text(undefined);
      unsentMessage = false;
      return;
    }

    if (unsentMessage) {
      self.comment().text(undefined);
      repository.load(self.applicationId());
      unsentMessage = false;
    }
  });

  hub.subscribe({type: "page-load"}, function(data) {
    if(_.contains(pages.concat("applications"), pageutil.getPage())) {
      refreshSidePanel(data.previousHash);
    }
    // Show side panel on specified pages
    if(_.contains(pages, pageutil.getPage())) {
      $("#side-panel-template").addClass("visible");
    }
  });

  repository.loaded(pages, function(application, applicationDetails) {
    if (!unsentMessage) {
      self.refresh(application, applicationDetails.authorities);
    }
  });
};

$(function() {
  "use strict";
  var sidePanel = new LUPAPISTE.SidePanelModel();
  $(document).keyup(function(e) {
    // esc hides the side panel
    if (e.keyCode === 27) {
      sidePanel.closeSidePanel();
    }
  });
  $("#side-panel-template").applyBindings(sidePanel);
});
