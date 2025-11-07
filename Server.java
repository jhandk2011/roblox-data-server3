// ---------------- Imports & Setup ----------------
import express from "express";
import fetch from "node-fetch";

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 8080;

// In-memory map to track visits (like ConcurrentHashMap)
const visitsMap = new Map();

// ---------------- POST /visits ----------------
// Increments a user's visits count in memory
app.post("/visits", (req, res) => {
  const { userId } = req.body;

  if (!userId || typeof userId !== "number") {
    return res.status(400).json({ error: "Invalid request (userId missing)" });
  }

  const count = (visitsMap.get(userId) || 0) + 1;
  visitsMap.set(userId, count);

  res.json({ success: true, totalVisits: count });
});

// ---------------- GET /user/:userId ----------------
// Returns Roblox user stats (visits, followers, join date)
app.get("/user/:userId", async (req, res) => {
  const userId = parseInt(req.params.userId, 10);
  if (isNaN(userId)) {
    return res.status(400).json({ error: "Invalid UserId" });
  }

  try {
    const [followers, joinDate, totalVisitsAPI] = await Promise.all([
      getFollowers(userId),
      getJoinDate(userId),
      getTotalVisits(userId),
    ]);

    const totalVisits = totalVisitsAPI + (visitsMap.get(userId) || 0);

    res.json({
      totalVisits,
      followers,
      joinDate,
    });
  } catch (err) {
    console.error("Error fetching data:", err);
    res.status(500).json({ error: "Internal Server Error" });
  }
});

// ---------------- Roblox API Functions ----------------

// Get followers count
async function getFollowers(userId) {
  try {
    const resp = await fetch(
      `https://friends.roproxy.com/v1/users/${userId}/followers/count`,
      { headers: { "User-Agent": "Mozilla/5.0" } }
    );

    if (!resp.ok) return 0;
    const data = await resp.json();
    return data.count || 0;
  } catch {
    return 0;
  }
}

// Get user join date
async function getJoinDate(userId) {
  try {
    const resp = await fetch(`https://users.roproxy.com/v1/users/${userId}`, {
      headers: { "User-Agent": "Mozilla/5.0" },
    });

    if (!resp.ok) return "Unknown";
    const data = await resp.json();
    return data.created || "Unknown";
  } catch {
    return "Unknown";
  }
}

// Get total visits (with pagination)
async function getTotalVisits(userId) {
  let total = 0;
  let cursor = null;
  const baseUrl = `https://games.roproxy.com/v2/users/${userId}/games?sortOrder=Asc&limit=100`;

  try {
    do {
      const url = cursor ? `${baseUrl}&cursor=${encodeURIComponent(cursor)}` : baseUrl;
      const resp = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0" } });
      if (!resp.ok) break;

      const data = await resp.json();
      if (data.data && Array.isArray(data.data)) {
        for (const game of data.data) {
          total += game.placeVisits || 0;
        }
      }

      cursor = data.nextPageCursor || null;
      if (cursor) await new Promise((r) => setTimeout(r, 200)); // avoid rate limits
    } while (cursor);
  } catch (err) {
    console.error("Error in getTotalVisits:", err);
  }

  return total;
}

// ---------------- Start Server ----------------
app.listen(PORT, () => {
  console.log(`✅ Server started on port ${PORT}`);
});
