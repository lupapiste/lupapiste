(function() {

  function getPwQuality(pw) {
    var l = pw.length;
    if (l <= 6)  return 0;
    if (l <= 8)  return 1;
    if (l <= 10) return 2;
    if (l <= 12) return 3;
    return 4;
  }

  function isNotBlank(s) { return !/^\s*$/.test(s); };
  function equals(s1, s2) { return s1 === s2; };
  
  var model = {
      passwd0: ko.observable(""),
      passwd1: ko.observable(""),
      passwd2: ko.observable(""),
      error: ko.observable(null)
  };

  model.clear = function() {
    model.passwd0("");
    model.passwd1("");
    model.passwd2("");
    model.error(null);
    $("#mypage input[name='passwd0']").focus();
    return model;
  }

  model.ok = ko.computed(function() {
    return isNotBlank(model.passwd0()) &&
      (getPwQuality(model.passwd1()) > 0) &&
      equals(model.passwd1(), model.passwd2());
  });
  
  model.noMatch = ko.computed(function() {
    return isNotBlank(model.passwd1()) && isNotBlank(model.passwd2()) && !equals(model.passwd1(), model.passwd2());
  });
  
  model.changePassword = function() {
    var t = setTimeout(function() { $("#mypage img").show(); }, 200);
    ajax
      .command("change-passwd", {"old-pw": model.passwd0(), "new-pw": model.passwd1()})
      .success(function() {
        model.clear();
      })
      .error(function(data) {
        model.clear().error(loc("passwd-change." + data.text));
      })
      .complete(function() {
        clearTimeout(t);
        $("#mypage img").hide();
      })
      .call();
  };

  var pwQualityData = ["poor", "low", "average", "good", "excellent"];
  
  model.pwQuality = ko.computed(function() {
    return pwQualityData[getPwQuality(model.passwd1())];
  });

  hub.onPageChange("mypage", model.clear);
  
  $(function() {
    ko.applyBindings(model, $("#mypage")[0]);
  });
  
})();
