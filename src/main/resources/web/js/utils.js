console.log("strt utils");

function loadLogin() {
	document.getElementById('LoginBox').style.display = ''; 
	document.getElementById('container').style.display = 'none';
	console.log("login loaded");
}

function loadUser() {
	console.log("account loaded");
    document.getElementById('LoginBox').style.display = 'none';  
    document.getElementById('container').style.display = '';
}


function login() {
	console.log("login()");
	var phoneNo = $('#phone').val().trim();
	var password = $('#password').val().trim();
	phoneNo = '+91' + phoneNo;
	console.log("sending : "+ phoneNo + password);
	
	authenticate(phoneNo, password);
	$('#phone').val('');
	$('#password').val('');
	return false;
}

function checkCreds() {
	if(!checkCookie()) {
		console.log("no cookie");
		loadLogin();
	} else {
	    console.log("yes cookie");
		loadAccount(sampleConversations,sampleContacts);
    // 	hike.conversationListView = new hike.ConversationListView(sampleConversations);
	}
	
}

function addFriend() {
	var name = $('#friendName').val().trim();
	var phoneNo = $('#friendPhoneNo').val().trim();
	
	hike.conversationListView.collection.add(
	{
		personName : name,
		phoneNo : '+91' + phoneNo,
	});
	
	hike.contactListView.collection.add(
	{
		name : name,
		phoneNo : '+91' + phoneNo,
	});
	$('#friendName').val('');
	$('#friendPhoneNo').val('');
	return false;
}

function sendMessage(type,msg)
{
	publish(createPacket(type,msg),TOKEN);
}

function receiveMessage(msg) {
	networkManager(msg);
}

function signOut() {
	deleteCookie("phone");
	deleteCookie("token");
	location.reload();
	return false;
}

function createGroup()
{
	if (grpCreated)
	{
		return false;
	}
	var msg =
	{
		'to' : UID + "123456789",
		"members" : [
		           {"msisdn":"+919582974797","name":"Gaurav Mittal"},
		           {"msisdn":"+919999900001","name":"Test User 1"}
		           ],
	};
	
	hike.conversationListView.collection.add(
	{
		personName : 'Test Group 1',
		phoneNo : UID + "123456789"
	});
	var conv = hike.conversationListView.collection.findWhere(
	{
		phoneNo : UID + "123456789"
	});
	conv.makeItGroup(msg.members);
	
	sendMessage("groupChatJoin", msg);
	grpCreated = true;
	return false;

}

function deleteGroup()
{
	if (!grpCreated)
	{
		return false;
	}
	var msg =
	{
		'to' : UID + "123456789",
	};
	
	hike.conversationListView.collection.remove(
		hike.conversationListView.collection.findWhere(
				{
					phoneNo : UID + "123456789"
				})
	);
	sendMessage("groupChatLeft", msg);
	grpCreated = false;
	return false;
}

console.log("end utils");
