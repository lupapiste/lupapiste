;(function($) {
  "use strict";

  function nop() { }

  function Tree(context, args) {
    var self = this;

    var template = args.template || $(".default-tree-template");
    self.linkTemplate = $(".tree-link", template);
    self.finalTemplate = $(".tree-final", template);
    var e = $(".tree-control", template).clone();
    context.append(e);
    self.content = $(".tree-content", e);
    self.content.click(function(event) {
      var e = getEvent(event);
      e.preventDefault();
      e.stopPropagation();
      self.clickHandler(e);
      return false;
    });

    self.onSelect = args.onSelect || nop;
    self.data = [];
    self.moveLeft  = {"margin-left": "-=400"};
    self.moveRight = {"margin-left": "+=400"};
    
    self.clickGo = function(e) {
      self.stateNop();
      var target = $(e.target),
          link = target.data("tree-link-data");
      if (!link) return false;
      var selectedLink = link[0],
          nextElement = link[1],
          next = _.isArray(nextElement) ? self.makeLinks(nextElement) : self.makeFinal(nextElement);
      self.model.stack.push(selectedLink);
      self.content.append(next).animate(self.moveLeft, 500, self.stateGo);
      return false;
    };
    
    self.goBack = function() {
      self.stateNop();
      self.model.stack.pop();
      self.content.animate(self.moveRight, 500, function() {
        $(".tree-page", self.content).filter(":last").remove();
        self.stateGo();
      });
      return false;
    };
    
    self.setClickHandler = function(handler) { self.clickHandler = handler; return self; }
    self.stateGo = _.partial(self.setClickHandler, self.clickGo);
    self.stateNop = _.partial(self.setClickHandler, nop);

    self.makeFinal = function(data) {
      return self.finalTemplate.clone().addClass("tree-page").applyBindings(data);
    }
    
    self.makeLinks = function(data) {
      return _.reduce(data, self.appendLink, $("<div>").addClass("tree-page"));
    };
    
    self.appendLink = function(div, linkData) {
      var link = self.linkTemplate
        .clone()
        .data("tree-link-data", linkData)
        .applyBindings(linkData[0]);
      return div.append(link);
    };
    
    self.reset = function(data) {
      self.stateNop();
      self.data = data;
      self.model.stack.removeAll();
      self.content.empty().css("margin-left", "400px").append(self.makeLinks(data)).animate(self.moveLeft, 500, self.stateGo);
      return self;
    };
    
    self.model = {
      stack: ko.observableArray([]),
      goBack: self.goBack,
      goStart: function() { self.reset(self.data); return false; }
    };

    ko.applyBindings(self.model, e[0]);
  }
  
  $.fn.applyBindings = function(model) {
    _.each(this, _.partial(ko.applyBindings, model));
    return this;
  };
  
  $.fn.selectTree = function(arg) {
    return _.isArray(arg) ? this.data("tree-data").reset(arg) : this.data("tree-data", new Tree(this, arg));
  };
  
})(jQuery);
