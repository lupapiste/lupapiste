var tree = (function() {
  "use strict";

  var setup = new function() {
    var self = this;
    self.speed = 200;
    self.animation = function(enabled) {
      self.speed = enabled ? 200 : 0;
    };
  }();

  function nop() { return true; }

  function Tree(context, args) {
    var self = this;

    args = args || {};

    var defaultTemplate = $(".default-tree-template");
    var template = args.template || defaultTemplate;

    var findTemplate = function(name) {
      if (args[name]) { return args[name]; }
      var e = $(".tree-" + name, template);
      return (e && e.length) ? e : $(".tree-" + name, defaultTemplate);
    };

    var titleTemplate = findTemplate("title");
    var contentTemplate = findTemplate("content");
    var navTemplate = findTemplate("nav");

    self.linkTemplate = findTemplate("link");
    self.lastTemplate = findTemplate("last");

    self.onSelect = args.onSelect || nop;
    self.baseModel = args.baseModel || {};
    self.data = [];
    self.width = args.width || context.width();
    self.moveLeft  = {"margin-left": "-=" + self.width};
    self.moveRight = {"margin-left": "+=" + self.width};

    function findTreeData(target) {
      var data = target.data("tree-link-data");
      if (data) { return data; }
      var parent = target.parent();
      return (parent.length) ? findTreeData(parent) : null;
    }

    self.clickGo = function(e) {
      var target = $(e.target),
          link = findTreeData(target);
      if (!link) { return true; }
      var selectedLink = link[0],
          nextElement = link[1],
          next = _.isArray(nextElement) ? self.makeLinks(nextElement) : self.makeFinal(nextElement);
          hub.send("track-click", {category:"Tree", label: selectedLink.op, event:"treeClick"});
      self.model.stack.push(selectedLink);
      self.stateNop().content.append(next).animate(self.moveLeft, setup.speed, self.stateGo);
      return false;
    };

    self.goBack = function() {
      hub.send("track-click", {category:"Tree", label:"treeBack", event:"treeBack"});
      if (self.model.stack().length < 1) { return false; }

      self.stateNop();
      self.model.stack.pop();
      self.content.animate(self.moveRight, setup.speed, function() {
        self.stateGo();
        $(".tree-page", self.content).filter(":last").remove();
      });

      if (self.model.selected()) {
        self.model.selected(null);
        self.onSelect(null);
      }

      return self;
    };

    self.setClickHandler = function(handler) { self.clickHandler = handler; return self; };
    self.stateGo = _.partial(self.setClickHandler, self.clickGo);
    self.stateNop = _.partial(self.setClickHandler, nop);

    self.makeFinal = function(data) {
      self.model.selected(data);
      self.onSelect(data);
      return self.lastTemplate
        .clone()
        .addClass("tree-page")
        .css("width", self.width + "px")
        .applyBindings(_.extend({}, self.baseModel, data));
    };

    self.makeLinks = function(data) {
      return _.reduce(data, self.appendLink, $("<div>").addClass("tree-page").css("width", self.width + "px"));
    };

    self.appendLink = function(div, linkData) {
      var link = self.linkTemplate
        .clone()
        .data("tree-link-data", linkData)
        .applyBindings(_.extend({}, self.baseModel, linkData[0]));
      return div.append(link);
    };

    self.reset = function(data) {
      self.stateNop();
      self.data = data;
      self.model.stack.removeAll();
      self.content.empty();
      if (self.data && self.data.length) {
        self.content
          .css("margin-left", "" + self.width + "px")
          .append(self.makeLinks(data))
          .animate({"margin-left": "0px"}, setup.speed, self.stateGo);
      }
      return self;
    };

    self.model = {
      stack: ko.observableArray([]),
      selected: ko.observable(),
      goBack: self.goBack,
      goStart: function() { self.reset(self.data); return false; }
    };

    self.content = contentTemplate.clone().click(function(e) { return self.clickHandler(getEvent(e)); });

    context
      .append(titleTemplate.clone())
      .append(self.content)
      .append(navTemplate.clone())
      .applyBindings(_.extend({}, self.baseModel, self.model));

    return _.assign(
        util.fluentify({
          reset:    self.reset,
          back:     self.model.goBack,
          start:    self.model.goStart,
          selected: self.model.selected
        }),
        {getSelected: function() {return self.model.selected();},
         getStack:    function() {return self.model.stack();}});

  }

  $.fn.selectTree = function(arg) {
    return new Tree(this, arg);
  };

  return setup;

})();
